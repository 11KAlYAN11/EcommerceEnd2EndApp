import { useState, useEffect } from 'react'
import { adminApi } from '../../api/admin'
import { categoriesApi } from '../../api/categories'
import { useToast } from '../../context/ToastContext'
import { formatPrice } from '../../utils/format'
import Modal from '../../components/common/Modal'
import Spinner from '../../components/common/Spinner'
import './Admin.css'

const EMPTY = { name: '', description: '', price: '', stockQuantity: '', categoryId: '', imageUrl: '' }

export default function AdminProductsPage() {
  const [products,   setProducts]   = useState([])
  const [categories, setCategories] = useState([])
  const [loading,    setLoading]    = useState(true)
  const [showModal,  setShowModal]  = useState(false)
  const [editing,    setEditing]    = useState(null)
  const [form,       setForm]       = useState(EMPTY)
  const [saving,     setSaving]     = useState(false)
  const [page,       setPage]       = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const toast = useToast()

  function load() {
    adminApi.getProducts({ page, size: 10 })
      .then(res => {
        const pageData = res.data
        setProducts(pageData.content || [])
        setTotalPages(pageData.totalPages || 0)
      })
      .catch(() => toast.error('Failed to load products'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    categoriesApi.getAll().then(r => setCategories(r.data || []))
  }, [])

  useEffect(() => { load() }, [page])

  function openCreate() {
    setEditing(null)
    setForm(EMPTY)
    setShowModal(true)
  }

  function openEdit(p) {
    setEditing(p)
    setForm({ name: p.name, description: p.description || '', price: p.price, stockQuantity: p.stockQuantity, categoryId: p.categoryId || '', imageUrl: p.imageUrl || '' })
    setShowModal(true)
  }

  async function handleSave() {
    setSaving(true)
    try {
      const payload = { ...form, price: parseFloat(form.price), stockQuantity: parseInt(form.stockQuantity) }
      if (editing) {
        await adminApi.updateProduct(editing.id, payload)
        toast.success('Product updated')
      } else {
        await adminApi.createProduct(payload)
        toast.success('Product created')
      }
      setShowModal(false)
      load()
    } catch (err) {
      toast.error(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('Delete this product?')) return
    try {
      await adminApi.deleteProduct(id)
      toast.success('Product deleted')
      load()
    } catch (err) {
      toast.error(err.message)
    }
  }

  const f = (k) => e => setForm(prev => ({ ...prev, [k]: e.target.value }))

  if (loading) return <div style={{ padding: '2rem' }}><Spinner /></div>

  return (
    <div className="admin-page">
      <div className="page-header">
        <h1 className="page-title">Products</h1>
        <button className="btn btn-primary" onClick={openCreate}>+ New Product</button>
      </div>

      <div className="card" style={{ overflow: 'auto' }}>
        <table className="table">
          <thead>
            <tr><th>Name</th><th>Category</th><th>Price</th><th>Stock</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {products.map(p => (
              <tr key={p.id}>
                <td>{p.name}</td>
                <td>{p.categoryName || '—'}</td>
                <td>{formatPrice(p.price)}</td>
                <td>{p.stockQuantity}</td>
                <td>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button className="btn btn-ghost btn-sm" onClick={() => openEdit(p)}>Edit</button>
                    <button className="btn btn-danger btn-sm" onClick={() => handleDelete(p.id)}>Delete</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="pagination">
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>&larr;</button>
          {[...Array(totalPages)].map((_, i) => (
            <button key={i} className={page === i ? 'active' : ''} onClick={() => setPage(i)}>{i + 1}</button>
          ))}
          <button disabled={page === totalPages - 1} onClick={() => setPage(p => p + 1)}>&rarr;</button>
        </div>
      )}

      <Modal
        open={showModal}
        onClose={() => setShowModal(false)}
        title={editing ? 'Edit Product' : 'New Product'}
        footer={
          <>
            <button className="btn btn-ghost" onClick={() => setShowModal(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </button>
          </>
        }
      >
        <div className="form-grid">
          <div className="form-group">
            <label className="form-label">Name</label>
            <input className="form-input" value={form.name} onChange={f('name')} placeholder="Product name" />
          </div>
          <div className="form-group">
            <label className="form-label">Category</label>
            <select className="form-input" value={form.categoryId} onChange={f('categoryId')}>
              <option value="">-- Select --</option>
              {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Price (INR)</label>
            <input className="form-input" type="number" value={form.price} onChange={f('price')} placeholder="0.00" />
          </div>
          <div className="form-group">
            <label className="form-label">Stock Quantity</label>
            <input className="form-input" type="number" value={form.stockQuantity} onChange={f('stockQuantity')} placeholder="0" />
          </div>
          <div className="form-group" style={{ gridColumn: '1 / -1' }}>
            <label className="form-label">Image URL</label>
            <input className="form-input" value={form.imageUrl} onChange={f('imageUrl')} placeholder="https://..." />
          </div>
          <div className="form-group" style={{ gridColumn: '1 / -1' }}>
            <label className="form-label">Description</label>
            <textarea className="form-input" rows="3" value={form.description} onChange={f('description')} placeholder="Product description..." />
          </div>
        </div>
      </Modal>
    </div>
  )
}
