import { requireUser } from "@/lib/auth/guard";
import { isLiveMode } from "@/lib/env";
import { javaAidRequest } from "@/lib/financial-aid/java-client";
import type { AidRecord, AidProgram } from "@/lib/financial-aid/contracts";
import { SessionProvider } from "@/components/providers/SessionProvider";
import DashboardShell from "@/components/layout/DashboardShell";
import FinancialAidView from "./FinancialAidView";

export const dynamic = "force-dynamic";
export default async function FinancialAidPage() {
  const user = await requireUser("/financial-aid");
  const [records, programs] = await Promise.all([
    javaAidRequest<AidRecord[]>(user, "/api/aid/applications"),
    javaAidRequest<AidProgram[]>(user, "/api/aid/programs"),
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
