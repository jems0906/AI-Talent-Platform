const API_BASE = import.meta.env.VITE_API_BASE || "/api";

const TRANSIENT_STATUSES = new Set([502, 503, 504]);

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function friendlyErrorFromResponse(status, text) {
  // Avoid surfacing raw nginx/html bodies to end users.
  if (TRANSIENT_STATUSES.has(status)) {
    return `Service is warming up (${status}). Please retry in a few seconds.`;
  }

  const trimmed = (text || "").trim();
  if (!trimmed) {
    return `Request failed (${status}).`;
  }

  if (trimmed.startsWith("<") && trimmed.includes("<html")) {
    return `Request failed (${status}). Please try again.`;
  }

  // Spring errors are often JSON; extract meaningful text instead of dumping payload.
  try {
    const parsed = JSON.parse(trimmed);
    if (parsed && typeof parsed === "object") {
      if (typeof parsed.message === "string" && parsed.message.trim()) {
        return parsed.message.trim();
      }

      if (status === 409) {
        return "Account already exists for this tenant and email. Please use Login.";
      }

      if (typeof parsed.error === "string" && parsed.error.trim()) {
        return `${parsed.error.trim()} (${status}).`;
      }
    }
  } catch {
    // Non-JSON plain text response; fall through.
  }

  if (status === 401) {
    return "Invalid credentials. Please check tenant, email, and password.";
  }

  if (status === 403) {
    return "Access denied for this tenant or role.";
  }

  return trimmed;
}

async function requestJson(path, options = {}, retryCount = 2) {
  const url = `${API_BASE}${path}`;

  for (let attempt = 0; attempt <= retryCount; attempt += 1) {
    try {
      const res = await fetch(url, options);
      if (res.ok) {
        return res.json();
      }

      const text = await res.text();
      if (TRANSIENT_STATUSES.has(res.status) && attempt < retryCount) {
        await delay(500 * (attempt + 1));
        continue;
      }

      throw new Error(friendlyErrorFromResponse(res.status, text));
    } catch (err) {
      if (attempt < retryCount) {
        await delay(500 * (attempt + 1));
        continue;
      }
      throw err;
    }
  }

  throw new Error("Request failed after retries.");
}

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function registerRecruiter(payload) {
  return requestJson("/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...payload, role: "RECRUITER" }),
  });
}

export async function loginRecruiter(payload) {
  return requestJson("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export async function applyCandidate(formData) {
  return requestJson("/candidates/apply", {
    method: "POST",
    body: formData,
  });
}

export async function listCandidates(tenantId, token) {
  return requestJson("/candidates", {
    headers: {
      "X-Tenant-Id": tenantId,
      ...authHeaders(token),
    },
  });
}

export async function rankCandidate(tenantId, token, candidateId, jobDescription) {
  return requestJson(`/candidates/${candidateId}/rank`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Tenant-Id": tenantId,
      ...authHeaders(token),
    },
    body: JSON.stringify({ jobDescription }),
  });
}

export async function inviteCandidate(tenantId, token, candidateId, subject, message) {
  return requestJson(`/candidates/${candidateId}/invite`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Tenant-Id": tenantId,
      ...authHeaders(token),
    },
    body: JSON.stringify({ subject, message }),
  });
}

export async function clearTestCandidates(tenantId, token) {
  return requestJson("/candidates/test-data", {
    method: "DELETE",
    headers: {
      "X-Tenant-Id": tenantId,
      ...authHeaders(token),
    },
  });
}
