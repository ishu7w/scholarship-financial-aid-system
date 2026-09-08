"use client";

import {
  useState,
  useTransition,
  useEffect,
  useRef,
  type ReactNode,
  type FormEvent,
} from "react";
import Link from "next/link";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Check,
  Wallet,
  Search,
} from "lucide-react";
import { motion, useReducedMotion } from "framer-motion";
import ChatAssistant from "@/components/chat/ChatAssistant";
import { Badge, GlassCard, StatPill } from "@/components/ui/primitives";
import {
  needSchema,
  type NeedInput,
  type AidRecord,
  type AidStatus,
  type AidProgram,
  type AssessmentResult,
} from "@/lib/financial-aid/contracts";
import { submitAid, changeAid, assessAid } from "@/lib/financial-aid/actions";

const rupees = (value: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(value);
const initialNeed: NeedInput = {
  annualIncome: 300000,
  tuition: 120000,
  livingCosts: 60000,
  existingSupport: 30000,
  contribution: 20000,
  emergency: false,
};
const fields: {
  key: Exclude<keyof NeedInput, "emergency">;
  label: string;
  hint: string;
}[] = [
  {
    key: "annualIncome",
    label: "Annual household income",
    hint: "All household income for one year",
  },
  {
    key: "tuition",
    label: "Annual tuition & fees",
    hint: "Your college or university fees",
  },
  {
    key: "livingCosts",
    label: "Annual living & study costs",
    hint: "Housing, books, transport, and equipment",
  },
  {
    key: "existingSupport",
    label: "Confirmed scholarships & aid",
    hint: "Include support already awarded",
  },
  {
    key: "contribution",
    label: "Family / personal contribution",
    hint: "Amount available toward these expenses",
  },
];
const inputClass = "input-premium mt-2 disabled:opacity-50";
const statusLabel = (status: AidStatus) => status.replaceAll("_", " ");

export default function FinancialAidView({
  records,
  aidPrograms,
  role,
  demo,
}: {
  records: AidRecord[];
  aidPrograms: AidProgram[];
  role: "student" | "institution" | "admin";
  demo: boolean;
}) {
  const [tab, setTab] = useState<"programs" | "applications" | "review">(
    "programs",
  );
  const [need, setNeed] = useState<NeedInput>(
    demo
      ? initialNeed
      : {
          annualIncome: 0,
          tuition: 0,
          livingCosts: 0,
          existingSupport: 0,
          contribution: 0,
          emergency: false,
        },
  );
  const [selected, setSelected] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [pending, startTransition] = useTransition();
  const validNeed = needSchema.safeParse(need);
  const [assessmentState, setAssessmentState] = useState<{
    key: string;
    data: AssessmentResult;
  } | null>(null);
  const [assessmentError, setAssessmentError] = useState("");
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("All");
  const needKey = validNeed.success ? JSON.stringify(validNeed.data) : "";
  useEffect(() => {
    if (!needKey) return;
    let active = true;
    const timer = setTimeout(async () => {
      try {
        const result = await assessAid(JSON.parse(needKey));
        if (!active) return;
        if (result.ok) {
          setAssessmentState({ key: needKey, data: result.data });
          setAssessmentError("");
        } else setAssessmentError(result.error);
      } catch {
        if (active)
          setAssessmentError(
            "Could not assess your funding. Check the Java backend and retry.",
          );
      }
    }, 250);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [needKey]);
  const assessmentResult =
    assessmentState?.key === needKey ? assessmentState.data : null;
  const financialNeed = assessmentResult ? { gap: assessmentResult.gap } : null;
  const assessmentFor = (id: string) =>
    assessmentResult?.assessments.find((a) => a.programId === id);
  const visiblePrograms = aidPrograms.filter(
    (p) =>
      (category === "All" || p.category === category) &&
      `${p.name} ${p.description}`.toLowerCase().includes(query.toLowerCase()),
  );
  const selectedProgram = aidPrograms.find((p) => p.id === selected);
  const tabs = [
    { id: "programs", label: "Explore aid" },
    { id: "applications", label: "Applications & payments" },
    ...(demo || role === "admin"
      ? [{ id: "review", label: demo ? "Demo review desk" : "Review desk" }]
      : []),
  ] as const;

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setMessage("");
    startTransition(async () => {
      try {
        const result = await submitAid({
          programId: selected,
          requested: Number(form.get("requested")),
          reason: form.get("reason"),
          need,
        });
        if (!result.ok)
          setMessage(result.error ?? "Could not submit application.");
        else {
          setSelected(null);
          setTab("applications");
          setMessage("Application submitted. Follow its progress below.");
        }
      } catch {
        setMessage(
          "Connection interrupted. Refresh to check your application before retrying.",
        );
      }
    });
  }

  return (
    <div className="mx-auto max-w-none space-y-6">
      {demo && (
        <p className="text-xs text-muted">
          Shared demo workspace · Use sample information only · Online records may reset · No real payments
        </p>
      )}
      <AidEntrance>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatPill label="Aid programs" value={aidPrograms.length} />
          <StatPill label="Applications" value={records.length} />
          <StatPill
            label="Approved support"
            value={rupees(records.reduce((s, r) => s + r.awarded, 0))}
          />
          <StatPill
            label="Disbursement recorded"
            value={rupees(
              records
                .filter((r) => r.status === "disbursed")
                .reduce((s, r) => s + r.awarded, 0),
            )}
            tone="success"
          />
        </div>
      </AidEntrance>
      <div
        className="flex flex-wrap gap-2 border-b border-foreground/20 pb-3"
        aria-label="Financial aid views"
      >
        {tabs.map((t) => (
          <button
            type="button"
            key={t.id}
            aria-pressed={tab === t.id}
            onClick={() => {
              setTab(t.id as typeof tab);
              setMessage("");
            }}
            className={
              tab === t.id
                ? "btn-primary !px-4 !py-2 text-sm"
                : "btn-ghost !px-4 !py-2 text-sm"
            }
          >
            {t.label}
          </button>
        ))}
      </div>
      {message && (
        <p role="status" className="hairline p-4 text-sm">
          {message}
        </p>
      )}
      {tab === "programs" && (
        <div className="grid gap-6 xl:grid-cols-3">
          <AidEntrance>
            <GlassCard>
              <div className="mb-5 flex items-center gap-3">
                <Wallet className="h-5 w-5" />
                <h2 className="font-semibold">Your funding picture</h2>
              </div>
              <p className="mb-5 text-sm text-muted">
                Enter annual amounts in Indian rupees. Estimates use your
                declared figures and remain subject to verification.
              </p>
              <div className="space-y-4">
                {fields.map((f) => (
                  <label className="block text-sm" key={f.key}>
                    {f.label}
                    <input
                      className={inputClass}
                      type="number"
                      min="0"
                      max="100000000"
                      step="1"
                      required
                      value={Number.isNaN(need[f.key]) ? "" : need[f.key]}
                      onChange={(e) =>
                        setNeed({
                          ...need,
                          [f.key]:
                            e.target.value === ""
                              ? NaN
                              : Number(e.target.value),
                        })
                      }
                    />
                    <span className="mt-1 block text-xs text-muted">
                      {f.hint}
                    </span>
                  </label>
                ))}
                <label className="flex items-start gap-3 text-sm">
                  <input
                    className="mt-1 accent-foreground"
                    type="checkbox"
                    checked={need.emergency}
                    onChange={(e) =>
                      setNeed({ ...need, emergency: e.target.checked })
                    }
                  />
                  I am facing an unexpected financial emergency.
                </label>
              </div>
              <div className="mt-6 border-t border-foreground/20 pt-5">
                <p className="mono-label text-muted">Estimated funding gap</p>
                <p className="mt-2 text-3xl font-semibold">
                  {financialNeed ? rupees(financialNeed.gap) : "—"}
                </p>
                <p className="mt-2 text-xs text-muted">
                  Tuition + living costs − confirmed support − contribution
                </p>
                {!validNeed.success && (
                  <p role="alert" className="mt-2 text-sm text-danger">
                    Enter whole rupee amounts between 0 and 10,00,00,000.
                  </p>
                )}
              </div>
              {assessmentError && (
                <p role="alert" className="mt-3 text-sm text-danger">
                  {assessmentError}
                </p>
              )}
              {validNeed.success && !assessmentResult && !assessmentError && (
                <p className="mt-3 text-xs text-muted" role="status">
                  Updating eligibility…
                </p>
              )}
            </GlassCard>
          </AidEntrance>
          <div className="space-y-4 xl:col-span-2">
            <div className="relative">
              <Search className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
              <input
                aria-label="Search aid programs"
                placeholder="Search by name or support type…"
                className="input-premium !pl-11"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <div className="flex flex-wrap gap-2">
              {["All", ...new Set(aidPrograms.map((p) => p.category))].map(
                (c) => (
                  <button
                    key={c}
                    type="button"
                    aria-pressed={category === c}
                    onClick={() => setCategory(c)}
                    className={`mono-label cursor-pointer border px-4 py-2 transition-colors ${category === c ? "border-foreground bg-foreground text-background" : "border-foreground/20 text-muted hover:border-foreground"}`}
                  >
                    {c}
                  </button>
                ),
              )}
            </div>
            {!visiblePrograms.length && (
              <GlassCard>
                <p className="text-sm text-muted">
                  No programs match your search.
                </p>
              </GlassCard>
            )}
            {visiblePrograms.map((p, i) => {
              const assessment = assessmentFor(p.id);
              const applied = records.some((r) => r.programId === p.id);
              return (
                <AidEntrance key={p.id} delay={i * 0.07}>
                  <GlassCard className="space-y-4 card-hover">
                    <div className="flex items-start justify-between gap-3">
                      <span className="mono-label text-muted">
                        {p.category}
                      </span>
                      <Badge
                        tone={assessment?.eligible ? "success" : "neutral"}
                      >
                        {assessment?.eligible
                          ? "Potentially eligible"
                          : "Check requirements"}
                      </Badge>
                    </div>
                    <h3 className="font-semibold">{p.name}</h3>
                    <p className="text-sm leading-relaxed text-muted">
                      {p.description}
                    </p>
                    <div className="flex flex-wrap items-end justify-between gap-4">
                      <div>
                        <p className="text-2xl font-semibold">
                          Up to {rupees(p.maximum)}
                        </p>
                        <p className="mt-1 text-xs text-muted">
                          Non-repayable assistance · Subject to review
                        </p>
                      </div>
                      <button
                        type="button"
                        disabled={
                          !assessment?.eligible || applied || role !== "student"
                        }
                        className="btn-primary !px-4 !py-2 text-sm disabled:cursor-not-allowed disabled:opacity-40"
                        onClick={() => {
                          setSelected(p.id);
                          setMessage("");
                        }}
                      >
                        {applied ? "Applied" : "Apply for aid"}
                        <ArrowUpRight className="h-4 w-4" />
                      </button>
                    </div>
                    <p className="border-t border-foreground/15 pt-3 text-sm text-muted">
                      {assessment?.eligible
                        ? `Your estimated support: ${rupees(assessment.estimate)}`
                        : (assessment?.reason ??
                          "Complete the funding picture to see your estimate.")}
                    </p>
                  </GlassCard>
                </AidEntrance>
              );
            })}
            <p className="text-xs leading-relaxed text-muted">
              Estimates for different programs are alternatives, not a combined
              award. Declare all confirmed support before submitting another
              request.{" "}
              <Link href="/dashboard/student/documents" className="underline">
                Manage supporting documents
              </Link>
              .
            </p>
          </div>
        </div>
      )}
      {(tab === "applications" || tab === "review") && (
        <div className="space-y-4">
          <div>
            <h3 className="text-2xl font-semibold">
              {tab === "review"
                ? "Application review desk"
                : "Your support, step by step"}
            </h3>
            <p className="mt-2 text-sm text-muted">
              {tab === "review"
                ? "Verify the declaration and supporting documents before approving an award. Record a disbursement only after payment has been completed outside this system."
                : "Submitted → Under review → Approved → Disbursement recorded"}
            </p>
          </div>
          {!records.length && (
            <GlassCard className="py-12 text-center">
              <ArrowDownLeft className="mx-auto mb-4 h-8 w-8 text-muted" />
              <h4 className="text-xl font-semibold">No applications yet</h4>
              <p className="mt-2 text-sm text-muted">
                Start with your funding picture and find the right support.
              </p>
              <button
                className="btn-primary mt-5 text-sm"
                onClick={() => setTab("programs")}
              >
                Explore aid
              </button>
            </GlassCard>
          )}
          {[...records]
            .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
            .map((record) => (
              <ApplicationCard
                key={record.id}
                record={record}
                aidPrograms={aidPrograms}
                review={tab === "review"}
                canWithdraw={role === "student"}
              />
            ))}
        </div>
      )}
      {selectedProgram && (
        <AidDialog pending={pending} close={() => setSelected(null)}>
          <form onSubmit={apply} className="space-y-5">
            <p className="mono-label text-muted">Financial aid application</p>
            <h3 id="apply-title" className="text-2xl font-semibold">
              {selectedProgram.name}
            </h3>
            <p className="text-sm text-muted">
              Your funding declaration will be saved with this application.
            </p>
            <label className="block text-sm">
              Requested amount (₹)
              <input
                autoFocus
                className={inputClass}
                name="requested"
                type="number"
                min="1"
                max={
                  financialNeed
                    ? (assessmentFor(selectedProgram.id)?.estimate ?? 0)
                    : 0
                }
                step="1"
                required
                defaultValue={
                  financialNeed
                    ? (assessmentFor(selectedProgram.id)?.estimate ?? 0)
                    : 0
                }
              />
            </label>
            <label className="block text-sm">
              Tell us why you need support
              <textarea
                name="reason"
                className={inputClass}
                rows={4}
                required
                minLength={20}
                maxLength={2000}
                placeholder="Explain the expenses and circumstances this assistance will help with."
              />
            </label>
            <label className="flex items-start gap-3 text-sm">
              <input type="checkbox" required className="mt-1" />I confirm that
              these figures are accurate and include all confirmed scholarships
              and aid.
            </label>
            {message && (
              <p role="alert" className="text-sm text-danger">
                {message}
              </p>
            )}
            <div className="flex flex-wrap gap-3">
              <button
                disabled={pending}
                className="btn-primary text-sm disabled:opacity-50"
              >
                {pending ? "Submitting…" : "Submit application"}
              </button>
              <button
                type="button"
                disabled={pending}
                className="btn-ghost text-sm"
                onClick={() => setSelected(null)}
              >
                Cancel
              </button>
            </div>
          </form>
        </AidDialog>
      )}
      <ChatAssistant />
    </div>
  );
}

function ApplicationCard({
  record,
  aidPrograms,
  review,
  canWithdraw,
}: {
  record: AidRecord;
  aidPrograms: AidProgram[];
  review: boolean;
  canWithdraw: boolean;
}) {
  const [pending, startTransition] = useTransition();
  const [message, setMessage] = useState("");
  const program = aidPrograms.find((p) => p.id === record.programId);
  function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const submitter = (event.nativeEvent as SubmitEvent)
      .submitter as HTMLButtonElement;
    const status = submitter.value;
    startTransition(async () => {
      try {
        const result = await changeAid({
          id: record.id,
          expectedVersion: record.version,
          status,
          note: data.get("note"),
          amount: Number(data.get("amount") ?? 0),
        });
        setMessage(
          result.ok
            ? "Application updated."
            : (result.error ?? "Update failed."),
        );
      } catch {
        setMessage(
          "Connection interrupted. Refresh to check the current status.",
        );
      }
    });
  }
  return (
    <GlassCard>
      <div className="flex flex-wrap justify-between gap-3">
        <div>
          <p className="mono-label text-muted">
            {record.studentName} · {record.createdAt.slice(0, 10)}
          </p>
          <h4 className="mt-2 text-xl font-semibold">
            {program?.name ?? record.programId}
          </h4>
        </div>
        <Badge
          tone={
            record.status === "approved" || record.status === "disbursed"
              ? "success"
              : record.status === "rejected"
                ? "danger"
                : "neutral"
          }
        >
          {statusLabel(record.status)}
        </Badge>
      </div>
      <div className="my-5 grid grid-cols-2 gap-3">
        <StatPill label="Requested" value={rupees(record.requested)} />
        <StatPill label="Awarded" value={rupees(record.awarded)} />
      </div>
      <p className="whitespace-pre-wrap break-words text-sm text-muted">
        {record.reason}
      </p>
      <details className="mt-5 border-t border-foreground/15 pt-4">
        <summary className="cursor-pointer text-sm font-medium">
          Funding declaration & activity
        </summary>
        <div className="mt-4 grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
          {fields.map((f) => (
            <p key={f.key} className="text-muted">
              {f.label}:{" "}
              <span className="text-foreground">
                {rupees(record.need[f.key])}
              </span>
            </p>
          ))}
          <p className="text-muted">
            Emergency: {record.need.emergency ? "Yes" : "No"}
          </p>
        </div>
        <ol className="mt-5 space-y-4">
          {record.history.map((entry, i) => (
            <li className="flex gap-3 text-sm" key={i}>
              <Check className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="capitalize">
                  {statusLabel(entry.status)}{" "}
                  <span className="text-xs normal-case text-muted">
                    · {entry.at.slice(0, 10)} · {entry.actor}
                  </span>
                </p>
                <p className="mt-1 whitespace-pre-wrap break-words text-muted">
                  {entry.note}
                </p>
              </div>
            </li>
          ))}
        </ol>
      </details>
      {((review &&
        ["submitted", "under_review", "approved"].includes(record.status)) ||
        (!review &&
          canWithdraw &&
          ["submitted", "under_review"].includes(record.status))) && (
        <form
          className="mt-5 space-y-3 border-t border-foreground/15 pt-4"
          onSubmit={update}
        >
          <label className="block text-sm">
            {review
              ? "Review note / payment reference"
              : "Reason for withdrawal"}
            <input
              className={inputClass}
              name="note"
              required
              minLength={5}
              maxLength={1000}
              placeholder={
                record.status === "approved"
                  ? "Payment reference and date"
                  : "Add a clear note to the activity history"
              }
            />
          </label>
          {review && record.status === "under_review" && (
            <label className="block text-sm">
              Award amount (₹)
              <input
                name="amount"
                className={inputClass}
                type="number"
                min="1"
                max={record.requested}
                step="1"
                required
                defaultValue={record.requested}
              />
            </label>
          )}
          <div className="flex flex-wrap gap-3">
            {review ? (
              <>
                {record.status === "submitted" && (
                  <button
                    disabled={pending}
                    value="under_review"
                    className="btn-primary text-sm"
                  >
                    Start review
                  </button>
                )}
                {record.status === "under_review" && (
                  <>
                    <button
                      disabled={pending}
                      value="approved"
                      className="btn-primary text-sm"
                    >
                      Approve aid
                    </button>
                    <button
                      disabled={pending}
                      value="rejected"
                      className="btn-ghost text-sm"
                    >
                      Reject application
                    </button>
                  </>
                )}
                {record.status === "approved" && (
                  <button
                    disabled={pending}
                    value="disbursed"
                    className="btn-primary text-sm"
                  >
                    Record completed disbursement
                  </button>
                )}
              </>
            ) : (
              <button
                disabled={pending}
                value="withdrawn"
                className="btn-ghost text-sm"
              >
                Withdraw application
              </button>
            )}
          </div>
        </form>
      )}
      {message && (
        <p className="mt-3 text-sm" role="status">
          {message}
        </p>
      )}
    </GlassCard>
  );
}

function AidDialog({
  children,
  pending,
  close,
}: {
  children: ReactNode;
  pending: boolean;
  close: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    const previousFocus = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    dialog?.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      dialog?.close();
      document.body.style.overflow = overflow;
      previousFocus?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby="apply-title"
      onCancel={(e) => {
        e.preventDefault();
        if (!pending) close();
      }}
      className="fixed inset-0 m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-xl overflow-y-auto border border-foreground/20 bg-background p-6 text-foreground backdrop:bg-black/40 sm:p-8"
    >
      {children}
    </dialog>
  );
}

function AidEntrance({
  children,
  delay = 0,
}: {
  children: ReactNode;
  delay?: number;
}) {
  const reduced = useReducedMotion();
  return (
    <motion.div
      initial={reduced ? false : { opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: reduced ? 0 : 0.55,
        delay: reduced ? 0 : delay,
        ease: [0.22, 1, 0.36, 1],
      }}
    >
      {children}
    </motion.div>
  );
}
