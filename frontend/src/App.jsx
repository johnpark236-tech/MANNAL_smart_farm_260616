import { useEffect, useState } from "react";
import { fetchAgriPrices, fetchStocks } from "./api/marketApi";
import AgriPricesPage from "./pages/AgriPricesPage";
import CollectPage from "./pages/CollectPage";
import DashboardPage from "./pages/DashboardPage";
import StocksPage from "./pages/StocksPage";

const navItems = [
  { id: "dashboard", label: "대시보드" },
  { id: "stocks", label: "주식 시세" },
  { id: "agri", label: "농산물 가격" },
  { id: "collect", label: "수집 관리" },
];

export default function App() {
  const [activePage, setActivePage] = useState("dashboard");
  const [stocks, setStocks] = useState([]);
  const [agriPrices, setAgriPrices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadData = async () => {
    setLoading(true);
    setError("");
    try {
      const [stockRows, agriRows] = await Promise.all([fetchStocks(), fetchAgriPrices()]);
      setStocks(stockRows);
      setAgriPrices(agriRows);
    } catch (err) {
      setError("데이터를 불러오지 못했습니다. 백엔드 실행 상태를 확인하세요.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <h1>시장 가격 대시보드</h1>
          <p>국내 주식 시세와 농산물 가격을 한 화면에서 확인합니다.</p>
        </div>
      </header>

      <nav className="nav-tabs" aria-label="화면 이동">
        {navItems.map((item) => (
          <button
            key={item.id}
            className={activePage === item.id ? "active" : ""}
            onClick={() => setActivePage(item.id)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {error && <div className="alert">{error}</div>}
      {loading && <div className="loading">데이터를 불러오는 중입니다.</div>}

      {!loading && activePage === "dashboard" && (
        <DashboardPage stocks={stocks} agriPrices={agriPrices} />
      )}
      {!loading && activePage === "stocks" && <StocksPage stocks={stocks} />}
      {!loading && activePage === "agri" && <AgriPricesPage agriPrices={agriPrices} />}
      {!loading && activePage === "collect" && <CollectPage onCollected={loadData} />}
    </div>
  );
}
