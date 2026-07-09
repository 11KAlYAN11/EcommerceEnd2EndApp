export default function Spinner({ size = 'md', text = '' }) {
  return (
    <div className="spinner-wrap" style={{ flexDirection: 'column', gap: '1rem' }}>
      <div className={`spinner${size === 'sm' ? ' spinner-sm' : ''}`} />
      {text && <p className="text-muted">{text}</p>}
    </div>
  )
}
