export default function SummaryCard({ title, value, description }) {
  return (
    <section className="summary-card">
      <h2>{title}</h2>
      <strong>{value}</strong>
      <p>{description}</p>
    </section>
  );
}
