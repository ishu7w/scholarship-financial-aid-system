"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export type RealtimeEvent = "*" | "INSERT" | "UPDATE" | "DELETE";
export interface UseRealtimeOptions {
  events?: readonly RealtimeEvent[];
  enabled?: boolean;
}

/** Refresh server-owned Java data without a separate database subscription. */
export function useRealtime(
  table: string,
  filter: string | null,
  onChange?: () => void,
  options: UseRealtimeOptions = {},
): void {
  const router = useRouter();
  const enabled = options.enabled ?? true;
  useEffect(() => {
    if (!enabled) return;
    const refresh = () => {
      if (document.visibilityState !== "visible") return;
      if (onChange) onChange();
      else router.refresh();
    };
    const timer = setInterval(refresh, 30_000);
    window.addEventListener("focus", refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", refresh);
    };
  }, [enabled, table, filter, onChange, router]);
}

export default useRealtime;
