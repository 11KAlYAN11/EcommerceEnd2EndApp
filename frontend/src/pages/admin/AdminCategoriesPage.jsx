import { useState, useEffect } from 'react'
import { adminApi } from '../../api/admin'
import { categoriesApi } from '../../api/categories'
import { useToast } from '../../context/ToastContext'
import Modal from '../../components/common/Modal'
import Spinner from '../../components/common/Spinner'
import './Admin.css'

export default function AdminCategoriesPage() {
  const [cats,    setCats]    = useState([])
  const [loading, setLoading] = useState(true)
  const [show,    setShow]    = useState(false)
  const [editing, setEditing] = useState(null)
  const [form,    setForm]    = useState({ name: '', description: '' })
  const [saving,  setSaving]  = useState(false)
  const toast = useToast()

  function load() {
    categoriesApi.getAll()
      .then(res => setCats(res.data || []))
      .catch(() => toast.error('Failed to load categories'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  function openCreate() { setEditing(null); setForm({ name: '', description: '' }); setShow(true) }
  function openEdit(c)  { setEditing(c); setForm({ name: c.name, description: c.description || '' }); setShow(true) }

  async function handleSave() {
    setSaving(true)
    try {
      if (editing) {
        await adminApi.updateCategory(editing.id, form)
        toast.success('Category updated')
      } else {
        await adminApi.createCategory(form)
        toast.success('Category created')
      }
      setShow(false)
      load()
    } catch (err) {
      toast.error(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('Delete this category?')) return
    try {
      await adminApi.deleteCategory(id)
      toast.success('Category deleted')
      load()
    } catch (err) {
      toast.error(err.message)
    }
  }

  if (loading) return <div style={{ padding: '2rem' }}><Spinner /></div>

  return (
    <div className="admin-page">
      <div className="page-header">
        <h1 className="page-title">Categories</h1>
        <button className="btn btn-primary" onClick={openCreate}>+ New Category</button>
      </div>

      <div className="card" style={{ overflow: 'auto' }}>
        <table className="table">
          <thead>
            <tr><th>#</th><th>Name</th><th>Description</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {cats.map(c => (
              <tr key={c.id}>
                <td>{c.id}</td>
                <td>{c.name}</td>
                <td>{c.description || '—'}</td>
                <td>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button className="btn btn-ghost btn-sm" onClick={() => openEdit(c)}>Edit</button>
                    <button className="btn btn-danger btn-sm" onClick={() => handleDelete(c.id)}>Delete</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal
        open={show}
        onClose={() => setShow(false)}
        title={editing ? 'Edit Category' : 'New Category'}
        footer={
          <>
            <button className="btn btn-ghost" onClick={() => setShow(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </button>
          </>
        }
      >
        <div className="form-group">
          <label className="form-label">Name</label>
          <input className="form-input" value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} placeholder="Category name" />
        </div>
        <div className="form-group" style={{ marginTop: '1rem' }}>
          <label className="form-label">Description</label>
          <textarea className="form-input" rows="3" value={form.description} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} placeholder="Optional description..." />
        </div>
      </Modal>
    </div>
  )
}
