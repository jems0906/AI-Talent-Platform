import { useState } from "react";
import {
  clearTestCandidates,
  inviteCandidate,
  listCandidates,
  loginRecruiter,
  rankCandidate,
  registerRecruiter,
} from "../api/client";

const PAGE_SIZE = 6;

export default function RecruiterDashboard() {
  const [tenantId, setTenantId] = useState("acme-corp");
  const [name, setName] = useState("Recruiter One");
  const [email, setEmail] = useState("recruiter@acme.com");
  const [password, setPassword] = useState("password123");
  const [token, setToken] = useState("");
  const [jobDescription, setJobDescription] = useState("Java Spring Boot React microservices AWS Docker REST API");
  const [candidates, setCandidates] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [page, setPage] = useState(1);
  const [message, setMessage] = useState("");
  const [toast, setToast] = useState(null);

  const showToast = (text, variant = "info") => {
    setToast({ text, variant });
    window.clearTimeout(window.__toastTimer);
    window.__toastTimer = window.setTimeout(() => setToast(null), 2600);
  };

  const register = async () => {
    try {
      const result = await registerRecruiter({ tenantId, name, email, password });
      setToken(result.token);
      setMessage("Recruiter registered and logged in.");
      showToast("Recruiter registered.", "success");
    } catch (err) {
      setMessage(`Register failed: ${err.message}`);
      showToast(`Register failed: ${err.message}`, "error");
    }
  };

  const login = async () => {
    try {
      const result = await loginRecruiter({ tenantId, email, password });
      setToken(result.token);
      setMessage("Logged in.");
      showToast("Login successful.", "success");
    } catch (err) {
      setMessage(`Login failed: ${err.message}`);
      showToast(`Login failed: ${err.message}`, "error");
    }
  };

  const refresh = async () => {
    if (!token) {
      setMessage("Please login first.");
      return;
    }
    try {
      const result = await listCandidates(tenantId, token);
      setCandidates(result);
      setPage(1);
      setMessage(`Loaded ${result.length} candidates.`);
      showToast(`Loaded ${result.length} candidates.`, "success");
    } catch (err) {
      setMessage(`Load failed: ${err.message}`);
      showToast(`Load failed: ${err.message}`, "error");
    }
  };

  const runRank = async (candidateId) => {
    if (!token) {
      setMessage("Please login first.");
      return;
    }
    try {
      await rankCandidate(tenantId, token, candidateId, jobDescription);
      await refresh();
      setMessage("Ranking updated.");
      showToast("Ranking updated.", "success");
    } catch (err) {
      setMessage(`Ranking failed: ${err.message}`);
      showToast(`Ranking failed: ${err.message}`, "error");
    }
  };

  const invite = async (candidateId, candidateEmail) => {
    if (!token) {
      setMessage("Please login first.");
      return;
    }
    try {
      await inviteCandidate(
        tenantId,
        token,
        candidateId,
        "Interview Invitation",
        `Hi ${candidateEmail}, you are invited for an interview with ${tenantId}.`
      );
      await refresh();
      setMessage("Interview invite sent.");
      showToast("Interview invite sent.", "success");
    } catch (err) {
      setMessage(`Invite failed: ${err.message}`);
      showToast(`Invite failed: ${err.message}`, "error");
    }
  };

  const clearTestData = async () => {
    if (!token) {
      setMessage("Please login first.");
      showToast("Please login first.", "error");
      return;
    }

    try {
      const result = await clearTestCandidates(tenantId, token);
      await refresh();
      setMessage(`Cleared ${result.deletedCount} test candidates.`);
      showToast(`Cleared ${result.deletedCount} test candidates.`, "success");
    } catch (err) {
      setMessage(`Clear failed: ${err.message}`);
      showToast(`Clear failed: ${err.message}`, "error");
    }
  };

  const normalizedSearch = searchTerm.trim().toLowerCase();
  const filteredCandidates = normalizedSearch
    ? candidates.filter((c) =>
      [c.name, c.email, c.status].join(" ").toLowerCase().includes(normalizedSearch)
    )
    : candidates;

  const totalPages = Math.max(1, Math.ceil(filteredCandidates.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages);
  const startIndex = (safePage - 1) * PAGE_SIZE;
  const pagedCandidates = filteredCandidates.slice(startIndex, startIndex + PAGE_SIZE);

  return (
    <section className="panel reveal delay-1">
      <h2>Recruiter Dashboard</h2>

      <div className="grid-form">
        <label>
          Tenant ID
          <input value={tenantId} onChange={(e) => setTenantId(e.target.value)} />
        </label>
        <label>
          Recruiter Name
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label>
          Email
          <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" />
        </label>
        <label>
          Password
          <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" />
        </label>
      </div>

      <div className="row-actions">
        <button type="button" onClick={register}>Register</button>
        <button type="button" onClick={login}>Login</button>
        <button type="button" onClick={refresh}>Refresh Candidates</button>
        <button type="button" onClick={clearTestData}>Clear Test Data</button>
      </div>

      <label className="full-row">
        Job Description for AI Ranking
        <textarea value={jobDescription} onChange={(e) => setJobDescription(e.target.value)} rows={4} />
      </label>

      <p className="token">Session Token: {token ? "available" : "none"}</p>

      <label className="full-row">
        Search Candidates
        <input
          value={searchTerm}
          onChange={(e) => {
            setSearchTerm(e.target.value);
            setPage(1);
          }}
          placeholder="Filter by name, email, or status"
        />
      </label>

      <div className="candidate-list">
        {pagedCandidates.map((c) => (
          <article key={c.id} className="candidate-card">
            <h3>{c.name}</h3>
            <p>{c.email}</p>
            <p>Status: {c.status}</p>
            <p>Score: {c.score}</p>
            <div className="row-actions">
              <button type="button" onClick={() => runRank(c.id)}>AI Rank</button>
              <button type="button" onClick={() => invite(c.id, c.email)}>Send Invite</button>
            </div>
          </article>
        ))}
      </div>

      <div className="row-actions page-controls">
        <button type="button" onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={safePage <= 1}>
          Previous
        </button>
        <span className="page-label">Page {safePage} of {totalPages}</span>
        <button
          type="button"
          onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          disabled={safePage >= totalPages}
        >
          Next
        </button>
      </div>

      <p className="status">Showing {pagedCandidates.length} of {filteredCandidates.length} candidates.</p>

      {message && <p className="status">{message}</p>}
      {toast && <p className={`toast ${toast.variant}`}>{toast.text}</p>}
    </section>
  );
}
