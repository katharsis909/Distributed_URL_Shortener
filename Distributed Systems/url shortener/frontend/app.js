const API_BASE = "http://localhost:8080";

function setStatus(message, tone = "idle") {
    const statusBox = document.getElementById("status-box");
    statusBox.textContent = message;
    statusBox.className = `status-box ${tone}`;
}

function showResult(shortCode) {
    const resultBox = document.getElementById("result-box");
    const resultCode = document.getElementById("result-code");
    const resultLink = document.getElementById("result-link");
    const href = `${API_BASE}/${shortCode}`;

    resultCode.textContent = shortCode;
    resultLink.textContent = href;
    resultLink.href = href;
    resultBox.classList.remove("hidden");
}

function hideResult() {
    document.getElementById("result-box").classList.add("hidden");
}

async function sendJson(url, method, body) {
    const response = await fetch(url, {
        method,
        headers: {
            "Content-Type": "application/json"
        },
        body: body ? JSON.stringify(body) : undefined
    });

    let payload = null;
    try {
        payload = await response.json();
    } catch (error) {
        payload = null;
    }

    return { response, payload };
}

window.addEventListener("DOMContentLoaded", () => {
    document.body.classList.add("ready");
    document.getElementById("api-base").textContent = API_BASE;

    document.getElementById("focus-create").addEventListener("click", () => {
        document.getElementById("user-id").focus();
    });

    document.getElementById("jump-tools").addEventListener("click", () => {
        document.getElementById("tools").scrollIntoView({ behavior: "smooth", block: "start" });
    });

    document.getElementById("shorten-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        hideResult();
        setStatus("Sending create request to router...", "pending");

        const userId = Number(document.getElementById("user-id").value);
        const originalUrl = document.getElementById("original-url").value.trim();
        const customCode = document.getElementById("custom-code").value.trim();

        try {
            const { response, payload } = await sendJson(`${API_BASE}/shorten`, "POST", {
                userId,
                originalUrl,
                customCode: customCode || null
            });

            if (!response.ok || !payload?.success) {
                setStatus(payload?.message || "Create request failed.", "error");
                return;
            }

            setStatus(payload.message || "Short URL created.", "success");
            showResult(payload.shortCode);
        } catch (error) {
            setStatus("Could not reach RouterService. Start backend services and try again.", "error");
        }
    });

    document.getElementById("resolve-form").addEventListener("submit", (event) => {
        event.preventDefault();
        const code = document.getElementById("resolve-code").value.trim();
        if (!code) {
            setStatus("Enter a short code to resolve.", "error");
            return;
        }

        setStatus(`Opening ${code} through RouterService...`, "pending");
        const openedWindow = window.open(`${API_BASE}/${code}`, "_blank", "noopener,noreferrer");
        if (openedWindow) {
            setStatus(
                `Opened ${code} in a new tab. If the short code exists, the browser will follow the redirect.`,
                "success"
            );
            return;
        }

        setStatus(
            `Browser blocked the new tab for ${code}. Allow pop-ups for this page and try again.`,
            "error"
        );
    });

    document.getElementById("delete-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        hideResult();
        const code = document.getElementById("delete-code").value.trim();
        if (!code) {
            setStatus("Enter a short code to delete.", "error");
            return;
        }

        setStatus(`Deleting ${code} through RouterService...`, "pending");

        try {
            const { response, payload } = await sendJson(`${API_BASE}/${code}`, "DELETE");
            if (!response.ok || !payload?.success) {
                setStatus(payload?.message || "Delete request failed.", "error");
                return;
            }

            setStatus(payload.message || "Short code deleted and recycled.", "success");
        } catch (error) {
            setStatus("Could not reach RouterService. Start backend services and try again.", "error");
        }
    });
});
