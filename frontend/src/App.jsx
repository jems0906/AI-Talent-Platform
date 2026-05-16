import { useState } from "react";
import CandidatePortal from "./pages/CandidatePortal";
import RecruiterDashboard from "./pages/RecruiterDashboard";

export default function App() {
  const [view, setView] = useState("candidate");

  return (
    <div className="app-shell">
      <header className="hero">
        <p className="hero-kicker">AI Talent Experience Platform</p>
        <h1>Attract, Screen, and Hire Faster</h1>
        <p className="hero-subtitle">
          A mini Phenom-style SaaS for multi-tenant recruiting with AI candidate scoring.
        </p>
        <div className="view-toggle">
          <button
            type="button"
            className={view === "candidate" ? "active" : ""}
            onClick={() => setView("candidate")}
          >
            Candidate Portal
          </button>
          <button
            type="button"
            className={view === "recruiter" ? "active" : ""}
            onClick={() => setView("recruiter")}
          >
            Recruiter Dashboard
          </button>
        </div>
      </header>

      <main className="content">
        {view === "candidate" ? <CandidatePortal /> : <RecruiterDashboard />}
      </main>
    </div>
  );
}
