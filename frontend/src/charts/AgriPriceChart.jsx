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

export default function AgriPriceChart({ rows }) {
  const firstItemName = rows[0]?.item_name;
  const data = rows
    .filter((row) => row.item_name === firstItemName)
    .slice()
    .sort((a, b) => a.price_date.localeCompare(b.price_date))
    .map((row) => ({
      date: row.price_date,
      price: Number(row.price),
    }));

  return (
    <div className="chart-box">
      <h2>{firstItemName ? `${firstItemName} 가격 변화` : "가격 변화"}</h2>
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" />
          <YAxis width={80} />
          <Tooltip formatter={(value) => `${Number(value).toLocaleString()}원`} />
          <Legend />
          <Line type="monotone" dataKey="price" name="가격" stroke="#16a34a" strokeWidth={3} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
