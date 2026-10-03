# Presentation walkthrough

Open the website and go to `/login`. Choose **Student demo**, **Institution demo**, or **Admin demo**. Use **Sign out** to return to the account choices. These are shared sample identities, not personal accounts.

## Suggested demonstration (5–8 minutes)

1. **Student profile:** Open My Profile. Change a sample academic field, save, and reload to show that the change is recorded. Show the profile score and scholarship matching explanations.
2. **Documents:** Upload a small sample PDF (maximum 5 MB) as an income certificate or marksheet. Its text must match the values on the sample profile for verification. A mismatched value is flagged; an image or scanned PDF without extractable text remains pending. Delete the document after the presentation. Never upload personal documents to the public demo.
3. **Scholarship application:** Open an eligible scholarship with a future deadline and click Apply now. Reload to show Submitted. If that program was already decided during a previous presentation, create a fresh program from the institution account first.
4. **Institution review:** Sign out, choose Institution demo, and locate the student in the approval queue. Approve or reject the application. Return to Student demo and show the decision and notification.
5. **Financial aid:** As the student, enter annual expenses and support. Show the funding gap and eligibility estimates. Choose Apply for aid, add a sample reason, confirm the declaration, and submit. In Demo review desk, add a note and Start review, then Approve aid, then record a simulated disbursement with a sample reference. No payment is sent.
6. **Resume and copilot:** Use the sample resume or upload a text-based PDF; show the resulting analysis. Ask the copilot about the profile score or scholarship eligibility.
7. **Administration:** Choose Admin demo to show application totals and the audit trail. The sample account roster is fixed; real account creation, password recovery and user-management changes require authenticated mode.

## Repeatable scholarship example

From Institution → Programs → New program, create a program with a future deadline, positive award amount, a description of at least 20 characters, and eligibility criteria that the sample student meets. Keep the status Active. After the demonstration, change it to Closed from the same program editor.

## Running locally

Install dependencies and build while online first, then run `npm start` and open `http://localhost:3000`. Keep the Java data folder to retain local records. Hosted profile, application, decision and document-verification records use the shared database and survive container changes. Original uploaded sample files remain temporary. The free database may pause after inactivity; resume it before a presentation if needed. Sample accounts work without email services; external AI and real authentication still need their providers.
