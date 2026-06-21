import StockPriceChart from "../charts/StockPriceChart";
import DataTable from "../components/DataTable";

const columns = [
  { key: "stock_name", label: "종목명" },
  { key: "stock_code", label: "종목코드" },
  { key: "trade_date", label: "날짜" },
  {
    key: "close_price",
    label: "종가",
    render: (row) => `${Number(row.close_price).toLocaleString()}원`,
  },
  {
    key: "volume",
    label: "거래량",
    render: (row) => Number(row.volume).toLocaleString(),
  },
];

export default function StocksPage({ stocks }) {
  return (
    <main className="page page-grid">
      <StockPriceChart rows={stocks} />
      <DataTable columns={columns} rows={stocks} emptyMessage="주식 데이터가 없습니다. 수집 관리 화면에서 수집을 실행하세요." />
    </main>
  );
}
