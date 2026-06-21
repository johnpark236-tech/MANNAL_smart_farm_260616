import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

export default function StockPriceChart({ rows }) {
  const firstStockName = rows[0]?.stock_name;
  const data = rows
    .filter((row) => row.stock_name === firstStockName)
    .slice()
    .sort((a, b) => a.trade_date.localeCompare(b.trade_date))
    .map((row) => ({
      date: row.trade_date,
      close: Number(row.close_price),
    }));

  return (
    <div className="chart-box">
      <h2>{firstStockName ? `${firstStockName} 종가 변화` : "종가 변화"}</h2>
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" />
          <YAxis width={80} />
          <Tooltip formatter={(value) => `${Number(value).toLocaleString()}원`} />
          <Legend />
          <Line type="monotone" dataKey="close" name="종가" stroke="#2563eb" strokeWidth={3} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
