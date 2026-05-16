import { useRef, useState } from "react";
import { applyCandidate } from "../api/client";

export default function CandidatePortal() {
  const [tenantId, setTenantId] = useState("acme-corp");
  const [jobId, setJobId] = useState("software-engineer");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [resume, setResume] = useState(null);
  const [message, setMessage] = useState("");
  const resumeInputRef = useRef(null);

  const submitApplication = async () => {
    if (!resume) {
      setMessage("Please upload a resume file.");
      return;
    }

    const formData = new FormData();
    formData.append("tenantId", tenantId);
    formData.append("jobId", jobId);
    formData.append("name", name);
    formData.append("email", email);
    formData.append("resume", resume);

    try {
      const result = await applyCandidate(formData);
      setMessage(`Application submitted. Candidate ID: ${result.id}`);
      setName("");
      setEmail("");
      setResume(null);
      if (resumeInputRef.current) {
        resumeInputRef.current.value = "";
      }
    } catch (err) {
      setMessage(`Submission failed: ${err.message}`);
    }
  };

  return (
    <section className="panel reveal">
      <h2>Candidate Application</h2>
      <div className="grid-form">
        <label>
          Tenant ID
          <input value={tenantId} onChange={(e) => setTenantId(e.target.value)} required />
        </label>
        <label>
          Job ID
          <input value={jobId} onChange={(e) => setJobId(e.target.value)} required />
        </label>
        <label>
          Full Name
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label className="full-row">
          Resume (txt or pdf)
          <input
            ref={resumeInputRef}
            type="file"
            accept=".txt,.pdf,.doc,.docx"
            onChange={(e) => setResume(e.target.files?.[0] || null)}
            required
          />
        </label>
        <button className="cta" type="button" onClick={submitApplication}>Apply Now</button>
      </div>
      {message && <p className="status">{message}</p>}
    </section>
  );
}
