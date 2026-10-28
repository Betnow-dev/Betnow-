import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const githubToken = Deno.env.get("GITHUB_TOKEN");
  const githubOwner = Deno.env.get("GITHUB_OWNER");
  const githubRepo = Deno.env.get("GITHUB_REPO");
  const githubRef = Deno.env.get("GITHUB_REF") ?? "main";
  const workflowFile = Deno.env.get("GITHUB_WORKFLOW_FILE") ?? "build-webview-app.yml";

  if (!supabaseUrl || !anonKey || !serviceRoleKey || !githubToken || !githubOwner || !githubRepo) {
    return jsonResponse({ error: "Build service is not configured" }, 503);
  }

  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) {
    return jsonResponse({ error: "Authentication required" }, 401);
  }

  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await userClient.auth.getUser();
  if (authError || !user) return jsonResponse({ error: "Invalid session" }, 401);

  const adminClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: adminRecord, error: adminError } = await adminClient
    .from("app_admins")
    .select("user_id")
    .eq("user_id", user.id)
    .maybeSingle();
  if (adminError || !adminRecord) return jsonResponse({ error: "Admin access required" }, 403);

  let appId: string;
  try {
    const body = await request.json();
    appId = body.app_id;
    if (typeof appId !== "string" || !/^[0-9a-f-]{36}$/i.test(appId)) {
      return jsonResponse({ error: "Invalid app_id" }, 400);
    }
  } catch {
    return jsonResponse({ error: "Invalid request body" }, 400);
  }

  const { data: app, error: appError } = await adminClient
    .from("app_projects")
    .select("id")
    .eq("id", appId)
    .maybeSingle();
  if (appError || !app) return jsonResponse({ error: "App not found" }, 404);

  const { data: build, error: buildError } = await adminClient
    .from("app_builds")
    .insert({ app_id: appId, requested_by: user.id, status: "queued" })
    .select("id")
    .single();
  if (buildError || !build) return jsonResponse({ error: "Could not queue build" }, 500);

  const githubResponse = await fetch(
    `https://api.github.com/repos/${encodeURIComponent(githubOwner)}/${encodeURIComponent(githubRepo)}/actions/workflows/${encodeURIComponent(workflowFile)}/dispatches`,
    {
      method: "POST",
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${githubToken}`,
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ ref: githubRef, inputs: { build_id: build.id } }),
    },
  );

  if (!githubResponse.ok) {
    await adminClient.from("app_builds").update({
      status: "failed",
      error_message: "GitHub could not start the build; check the build service configuration.",
      finished_at: new Date().toISOString(),
    }).eq("id", build.id);
    return jsonResponse({ error: "Could not start cloud build" }, 502);
  }

  return jsonResponse({ build_id: build.id }, 202);
});
