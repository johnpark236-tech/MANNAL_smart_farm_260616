import SummaryCard from "../components/SummaryCard";

function latestCreatedAt(rows) {
  const latest = rows
    .map((row) => row.created_at)
    .filter(Boolean)
    .sort()
    .at(-1);
  return latest ? new Date(latest).toLocaleString("ko-KR") : "아직 수집 전";
}

export default function DashboardPage({ stocks, agriPrices }) {
  const stockNames = new Set(stocks.map((row) => row.stock_name));
  const agriItems = new Set(agriPrices.map((row) => row.item_name));
  const lastCollected = latestCreatedAt([...stocks, ...agriPrices]);

  return (
    <main className="page">
      <section className="summary-grid">
        <SummaryCard
          title="주식 시세"
          value={`${stockNames.size}개 종목`}
          description={`${stocks.length}건의 시세 데이터가 저장되어 있습니다.`}
        />
        <SummaryCard
          title="농산물 가격"
          value={`${agriItems.size}개 품목`}
          description={`${agriPrices.length}건의 가격 데이터가 저장되어 있습니다.`}
        />
        <SummaryCard title="최근 수집 시간" value={lastCollected} description="수집 버튼 실행 후 갱신됩니다." />
      </section>
    </main>
  );
}
