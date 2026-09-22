import { requireUser } from "@/lib/auth/guard";
import { isLiveMode } from "@/lib/env";
import { javaHttpRequest } from "@/lib/java/http";
import type { AidRecord, AidProgram } from "@/lib/financial-aid/contracts";
import { SessionProvider } from "@/components/providers/SessionProvider";
import DashboardShell from "@/components/layout/DashboardShell";
import FinancialAidView from "./FinancialAidView";

export const dynamic = "force-dynamic";
export default async function FinancialAidPage() {
  const user = await requireUser("/financial-aid");
  const [records, programs] = await Promise.all([
    javaHttpRequest<AidRecord[]>(user, "/api/aid/applications"),
    javaHttpRequest<AidProgram[]>(user, "/api/aid/programs"),
  ]);
  return (
    <SessionProvider user={user}>
      <DashboardShell
        title="Financial Aid"
        subtitle="Need-based assistance · Applications & disbursements"
      >
        {!records.ok || !programs.ok ? (
          <div role="alert" className="glass p-6">
            {!records.ok
              ? records.error
              : !programs.ok
                ? programs.error
                : "Financial aid is unavailable."}
          </div>
        ) : (
          <FinancialAidView
            records={records.data}
            aidPrograms={programs.data}
            role={user.role}
            demo={!isLiveMode()}
          />
        )}
      </DashboardShell>
    </SessionProvider>
  );
}
