// Display metadata and response types. Calculations live in the Java modules.
export const WEIGHTS = [
  {
    key: "academic",
    label: "Academic Performance",
    weight: 0.22,
  },
  {
    key: "financialNeed",
    label: "Financial Need",
    weight: 0.18,
  },
  {
    key: "achievements",
    label: "Achievements",
    weight: 0.12,
  },
  {
    key: "research",
    label: "Research Output",
    weight: 0.1,
  },
  {
    key: "leadership",
    label: "Leadership",
    weight: 0.08,
  },
  {
    key: "projects",
    label: "Projects & Skills",
    weight: 0.08,
  },
  {
    key: "community",
    label: "Community Service",
    weight: 0.07,
  },
  {
    key: "sop",
    label: "Statement of Purpose",
    weight: 0.08,
  },
  {
    key: "recommendation",
    label: "Recommendations",
    weight: 0.04,
  },
  {
    key: "behaviour",
    label: "Behaviour & Integrity",
    weight: 0.03,
  },
];

export interface RoadmapItem {
  quarter: string;
  title: string;
  detail: string;
  impact: number; // projected score gain
}

export interface ResumeAnalysis {
  atsScore: number;
  resumeScore: number;
  extracted: {
    education: string[];
    skills: string[];
    projects: string[];
    experience: string[];
    achievements: string[];
    certifications: string[];
  };
  suggestions: string[];
}
