import { isLiveMode } from "@/lib/env";
import LoginView from "./LoginView";

export default function LoginPage() {
  return <LoginView demoEnabled={!isLiveMode()} />;
}
