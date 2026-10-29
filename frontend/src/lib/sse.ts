/**
 * Server-Sent Events (SSE) placeholder for live class availability / assistant streaming.
 */

export interface SseSubscriptionOptions {
  onMessage: (data: any) => void;
  onError?: (err: any) => void;
}

export function subscribeToEvents(
  _url: string,
  _options: SseSubscriptionOptions
): () => void {
  // Placeholder for future SSE stream connections
  return () => {
    // Teardown logic
  };
}
