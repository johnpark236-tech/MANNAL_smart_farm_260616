import { useState } from "react";
import { collectAgriPrices, collectStocks } from "../api/marketApi";

export default function CollectPage({ onCollected }) {
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const runCollect = async (type) => {
    setBusy(true);
    setMessage("");
    try {
      const result = type === "stocks" ? await collectStocks() : await collectAgriPrices();
      setMessage(`${result.message} 신규 ${result.inserted}건, 갱신 ${result.updated}건`);
      await onCollected();
    } catch (err) {
      setMessage("수집 실행에 실패했습니다. 백엔드와 DB 상태를 확인하세요.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="page">
      <section className="collect-panel">
        <h2>데이터 수집 관리</h2>
        <div className="button-row">
          <button className="primary-button" disabled={busy} onClick={() => runCollect("stocks")}>
            주식 데이터 수집
          </button>
          <button className="secondary-button" disabled={busy} onClick={() => runCollect("agri")}>
            농산물 가격 수집
          </button>
        </div>
        {message && <p className="result-message">{message}</p>}
      </section>
    </main>
  );
}
