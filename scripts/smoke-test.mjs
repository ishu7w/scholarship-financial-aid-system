import assert from "node:assert/strict";

const base = process.env.SMOKE_BASE_URL ?? "http://localhost:3000";
const pages = [
  ["/", "Every deserving student"],
  ["/catalogue", "NATIONAL MERIT"],
  ["/dashboard/student", "Welcome back"],
  ["/scholarships", "Scholarship Explorer"],
  ["/scholarships/sch-merit-excellence", "National Merit Excellence Award"],
  ["/dashboard/student/profile", "Academic profile"],
  ["/dashboard/student/documents", "Documents"],
  ["/resume-analyzer", "Resume Analyzer"],
  ["/financial-aid", "Financial Aid"],
];
for (const [path, text] of pages) {
  const response = await fetch(`${base}${path}`, {
    signal: AbortSignal.timeout(30_000),
  });
  assert.equal(response.status, 200, path);
  assert.ok(
    (await response.text()).includes(text),
    `${path} should render ${text}`,
  );
  console.log(`PASS ${path}`);
}
const resume = await fetch(`${base}/api/resume/analyze`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    text: "Bachelor at State University\nBuilt a Java and SQL project for 500 students\nSoftware engineer internship improved performance by 30%",
  }),
});
assert.equal(resume.status, 200);
const analysis = (await resume.json()).analysis;
assert.ok(analysis.atsScore > 0 && analysis.atsScore <= 100);
assert.ok(analysis.extracted.skills.includes("Java"));
console.log("PASS Java resume analysis through Next.js");
const chat = await fetch(`${base}/api/chat`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ message: "What is my profile score?" }),
});
assert.equal(chat.status, 200);
assert.match((await chat.json()).reply, /profile score is \d+\/100/);
console.log("PASS Java copilot through Next.js");
