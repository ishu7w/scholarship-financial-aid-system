import "server-only";
import { javaRequest } from "@/lib/java/client";
export function deterministicAnswer(question: string): Promise<string> {
  return javaRequest<string>("chat", { question });
}
