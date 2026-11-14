import { NextResponse } from "next/server";

/**
 * Liveness of the Node process for the container HEALTHCHECK (docker/web.Dockerfile).
 * No auth, no backend call: a slow backend must not get the web container restarted.
 */
export const dynamic = "force-dynamic";

const HEALTH_STATUS_UP = "UP";

export function GET() {
  return NextResponse.json({ status: HEALTH_STATUS_UP });
}
