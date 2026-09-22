"use server";
import { javaRequest } from "@/lib/java/client";
import { getSessionProfile } from "@/lib/auth/session";
export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  body: string | null;
  href: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationFeed {
  items: NotificationItem[];
  unread: number;
}

export async function getMyNotifications(): Promise<NotificationFeed> {
  if (!(await getSessionProfile())) return { items: [], unread: 0 };
  return javaRequest("notifications");
}
export async function markAllNotificationsReadAction(): Promise<NotificationFeed> {
  if (!(await getSessionProfile())) return { items: [], unread: 0 };
  return javaRequest("notifications-read");
}
export async function markNotificationReadAction(
  id: string,
): Promise<NotificationFeed> {
  if (!(await getSessionProfile())) return { items: [], unread: 0 };
  return javaRequest("notifications-read", { id });
}
