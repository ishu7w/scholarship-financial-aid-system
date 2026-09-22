import { redirect } from "next/navigation";
import { javaRequest } from "@/lib/java/client";
import { getSessionProfile } from "@/lib/auth/session";
import { getDataSource } from "@/lib/datasource";
import InstitutionDashboardView, {
  NoInstitutionNotice,
  type RankedApplicant,
} from "./InstitutionDashboardView";

export default async function InstitutionDashboardPage() {
  const me = await getSessionProfile();
  if (!me) redirect("/login?next=/dashboard/institution");

  const ds = await getDataSource();
  const institution = await ds.getInstitutionForProfile(me.id);
  if (!institution) return <NoInstitutionNotice name={me.name} />;

  const [ranked, aggregates] = await Promise.all([
    javaRequest<RankedApplicant[]>("institution-ranked", { id: institution.id }),
    ds.getInstitutionAggregates(institution.id),
  ]);

  return (
    <InstitutionDashboardView
      orgName={institution.orgName}
      applicants={ranked}
      aggregates={aggregates}
      readOnly={ds.mode === "demo"}
    />
  );
}
