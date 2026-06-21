const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, options);
  if (!response.ok) {
    throw new Error(`API 요청 실패: ${response.status}`);
  }
  return response.json();
}

export function fetchStocks() {
  return request("/api/stocks");
}

export function fetchAgriPrices() {
  return request("/api/agri-prices");
}

export function collectStocks() {
  return request("/api/collect/stocks", { method: "POST" });
}

export function collectAgriPrices() {
  return request("/api/collect/agri-prices", { method: "POST" });
}
