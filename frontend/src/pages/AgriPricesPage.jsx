import AgriPriceChart from "../charts/AgriPriceChart";
import DataTable from "../components/DataTable";

const columns = [
  { key: "item_name", label: "품목명" },
  { key: "market_name", label: "시장명" },
  { key: "unit", label: "단위" },
  {
    key: "price",
    label: "가격",
    render: (row) => `${Number(row.price).toLocaleString()}원`,
  },
  { key: "price_date", label: "날짜" },
];

export default function AgriPricesPage({ agriPrices }) {
  return (
    <main className="page page-grid">
      <AgriPriceChart rows={agriPrices} />
      <DataTable columns={columns} rows={agriPrices} emptyMessage="농산물 데이터가 없습니다. 수집 관리 화면에서 수집을 실행하세요." />
    </main>
  );
}
