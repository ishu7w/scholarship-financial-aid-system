import { computeAIScore, rankScholarships, generateRoadmap } from "@/lib/ai-engine";
import { redirect } from "next/navigation";
import { getSessionProfile } from "@/lib/auth/session";
import { getDataSource } from "@/lib/datasource";
import StudentDashboardView from "./StudentDashboardView";
import EmptyProfileNotice from "./EmptyProfileNotice";

export default async function StudentDashboardPage() {
  const me = await getSessionProfile();
  if (!me) redirect("/login?next=/dashboard/student");

  const ds = await getDataSource();
  const [student, scholarships, applications] = await Promise.all([
    ds.getStudentProfile(me.id),
    ds.getScholarships(),
    ds.getApplications(me.id),
  ]);

  // A freshly registered account has no academic data yet — the engine
  // needs inputs before any score is meaningful.
  if (!student) return <EmptyProfileNotice name={me.name} />;

  const [ai, ranked, roadmapItems] = await Promise.all([
    computeAIScore(student),
    rankScholarships(student, scholarships),
    generateRoadmap(student),
  ]);

  return (
    <StudentDashboardView
      ai={ai}
      ranked={ranked}
      roadmapItems={roadmapItems}
      student={student}
      scholarships={scholarships}
      applications={applications}
    />
  );
}
