import { useState, useEffect, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { productsApi } from '../../api/products'
import { categoriesApi } from '../../api/categories'
import { cartApi } from '../../api/cart'
import { useAuth } from '../../context/AuthContext'
import { useCart } from '../../context/CartContext'
import { useToast } from '../../context/ToastContext'
import { formatPrice, imgFallback, PRODUCT_PLACEHOLDER } from '../../utils/format'
import Spinner from '../../components/common/Spinner'
import './Products.css'

export default function ProductListPage() {
  const [products,   setProducts]   = useState([])
  const [categories, setCategories] = useState([])
  const [loading,    setLoading]    = useState(true)
  const [search,     setSearch]     = useState('')
  const [searchInput,setSearchInput]= useState('')
  const [categoryId, setCategoryId] = useState('')
  const [page,       setPage]       = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [addingId,   setAddingId]   = useState(null)

  const { token } = useAuth()
  const { refreshCart } = useCart()
  const toast    = useToast()
  const navigate = useNavigate()

  // Fetch categories once on mount
  useEffect(() => {
    categoriesApi.getAll()
      .then(res => setCategories(res.data || []))
      .catch(() => {})
  }, [])

  // Fetch products whenever filter/page changes
  useEffect(() => {
    setLoading(true)
    const params = { page, size: 12, sortBy: 'name' }
    let req

    if (search) {
      req = productsApi.search({ q: search, categoryId: categoryId || undefined, page, size: 12 })
    } else if (categoryId) {
      req = productsApi.getByCategory(categoryId, { page, size: 12 })
    } else {
      req = productsApi.getAll(params)
    }

    req.then(res => {
      const pageData = res.data
      setProducts(pageData.content || [])
      setTotalPages(pageData.totalPages || 0)
    })
    .catch(() => toast.error('Failed to load products'))
    .finally(() => setLoading(false))
  }, [search, categoryId, page])

  function handleSearch(e) {
    e.preventDefault()
    setSearch(searchInput)
    setPage(0)
  }

  function handleCategory(id) {
    setCategoryId(id)
    setPage(0)
    setSearch('')
    setSearchInput('')
  }

  async function handleAddToCart(product) {
    if (!token) { navigate('/login'); return }
    setAddingId(product.id)
    try {
      await cartApi.addItem(product.id, 1)
      await refreshCart()
      toast.success(`${product.name} added to cart`)
    } catch (err) {
      toast.error(err.message)
    } finally {
      setAddingId(null)
    }
  }

  return (
    <div className="products-layout">
      {/* Sidebar */}
      <aside className="products-sidebar">
        <h3 className="sidebar-title">Categories</h3>
        <ul className="category-list">
          <li>
            <button
              className={`category-btn ${!categoryId ? 'active' : ''}`}
              onClick={() => handleCategory('')}
            >All Products</button>
          </li>
          {categories.map(c => (
            <li key={c.id}>
              <button
                className={`category-btn ${categoryId == c.id ? 'active' : ''}`}
                onClick={() => handleCategory(c.id)}
              >{c.name}</button>
            </li>
          ))}
        </ul>
      </aside>

      {/* Main */}
      <main className="products-main">
        {/* Search bar */}
        <form onSubmit={handleSearch} className="search-bar">
          <input
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Search products…"
            className="search-input"
          />
          <button type="submit" className="btn btn-primary">Search</button>
          {(search || categoryId) && (
            <button type="button" className="btn btn-outline"
              onClick={() => { setSearch(''); setSearchInput(''); setCategoryId(''); setPage(0) }}>
              Clear
            </button>
          )}
        </form>

        {loading ? <Spinner text="Loading products…" /> : (
          <>
            {products.length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon">📦</div>
                <h3>No products found</h3>
                <p>Try a different search or category</p>
              </div>
            ) : (
              <div className="grid-4">
                {products.map(p => (
                  <ProductCard key={p.id} product={p}
                    onAddToCart={handleAddToCart}
                    adding={addingId === p.id}
                  />
                ))}
              </div>
            )}

            {totalPages > 1 && (
              <div className="pagination">
                <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>← Prev</button>
                {[...Array(totalPages)].map((_, i) => (
                  <button key={i} className={page === i ? 'active' : ''} onClick={() => setPage(i)}>
                    {i + 1}
                  </button>
                ))}
                <button disabled={page === totalPages - 1} onClick={() => setPage(p => p + 1)}>Next →</button>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  )
}

function ProductCard({ product, onAddToCart, adding }) {
  const stockLow = product.stockQuantity > 0 && product.stockQuantity <= 5

  return (
    <div className="product-card">
      <Link to={`/products/${product.id}`} className="product-img-wrap">
        <img
          src={product.imageUrl || PRODUCT_PLACEHOLDER}
          alt={product.name}
          onError={imgFallback}
        />
      </Link>
      <div className="product-info">
        <Link to={`/products/${product.id}`} className="product-name">{product.name}</Link>
        <p className="product-desc">{product.description}</p>
        <div className="product-footer">
          <span className="product-price">{formatPrice(product.price)}</span>
          <div className="product-stock">
            {product.stockQuantity === 0
              ? <span className="badge badge-red">Out of stock</span>
              : stockLow
                ? <span className="badge badge-yellow">Only {product.stockQuantity} left</span>
                : <span className="badge badge-green">In stock</span>
            }
          </div>
        </div>
        <button
          className="btn btn-primary btn-full mt-1"
          disabled={product.stockQuantity === 0 || adding}
          onClick={() => onAddToCart(product)}
        >
          {adding ? <><span className="spinner spinner-sm" /> Adding…</> : 'Add to Cart'}
        </button>
      </div>
    </div>
  )
}
