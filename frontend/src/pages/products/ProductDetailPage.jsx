import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { productsApi } from '../../api/products'
import { cartApi } from '../../api/cart'
import { useAuth } from '../../context/AuthContext'
import { useCart } from '../../context/CartContext'
import { useToast } from '../../context/ToastContext'
import { formatPrice, imgFallback, PRODUCT_PLACEHOLDER } from '../../utils/format'
import Spinner from '../../components/common/Spinner'
import './Products.css'
import './ProductDetail.css'

// Fake discount % to make it look like Flipkart
function getDiscount(price) {
  const seeds = [10, 15, 18, 20, 22, 25, 30]
  return seeds[Math.floor(price) % seeds.length]
}

// Generate pseudo-gallery thumbnails from the same Unsplash URL with different params
function getGallery(imageUrl) {
  if (!imageUrl) return []
  const base = imageUrl.split('?')[0]
  return [
    imageUrl,
    base + '?w=600&q=80&crop=entropy',
    base + '?w=600&q=80&sat=-100',          // greyscale feel
    base + '?w=600&q=80&flip=h',            // flipped variant
  ]
}

// Split description into bullet highlights (sentences)
function getHighlights(desc) {
  if (!desc) return []
  return desc.split(/[.!]/).map(s => s.trim()).filter(s => s.length > 10).slice(0, 5)
}

export default function ProductDetailPage() {
  const { id } = useParams()
  const [product,   setProduct]   = useState(null)
  const [related,   setRelated]   = useState([])
  const [loading,   setLoading]   = useState(true)
  const [activeImg, setActiveImg] = useState(0)
  const [activeTab, setActiveTab] = useState('desc')
  const [qty,       setQty]       = useState(1)
  const [adding,    setAdding]    = useState(false)
  const [pincode,   setPincode]   = useState('')
  const [delivery,  setDelivery]  = useState(null)

  const { token } = useAuth()
  const { refreshCart } = useCart()
  const toast    = useToast()
  const navigate = useNavigate()

  useEffect(() => {
    setLoading(true)
    setActiveImg(0)
    setActiveTab('desc')
    setQty(1)
    productsApi.getById(id)
      .then(res => {
        const p = res.data
        setProduct(p)
        // Fetch related products from same category
        if (p?.categoryId) {
          productsApi.getByCategory(p.categoryId, { page: 0, size: 6 })
            .then(r => setRelated((r.data?.content || []).filter(x => x.id !== p.id).slice(0, 4)))
            .catch(() => {})
        }
      })
      .catch(() => toast.error('Product not found'))
      .finally(() => setLoading(false))
  }, [id])

  async function handleAddToCart() {
    if (!token) { navigate('/login'); return }
    setAdding(true)
    try {
      await cartApi.addItem(product.id, qty)
      await refreshCart()
      toast.success(`${product.name} × ${qty} added to cart`)
    } catch (err) {
      toast.error(err.message)
    } finally {
      setAdding(false)
    }
  }

  function checkDelivery() {
    if (pincode.length !== 6) { toast.error('Enter a valid 6-digit pincode'); return }
    setDelivery(`Delivery by ${getDeliveryDate()} — Free`)
  }

  if (loading) return <div style={{ padding: '2rem' }}><Spinner /></div>
  if (!product) return <div className="pdp-wrap"><p style={{padding:'2rem'}}>Product not found.</p></div>

  const discount    = getDiscount(product.price)
  const mrp         = Math.round(product.price / (1 - discount / 100))
  const gallery     = getGallery(product.imageUrl)
  const highlights  = getHighlights(product.description)
  const stockLow    = product.stockQuantity > 0 && product.stockQuantity <= 5
  const outOfStock  = product.stockQuantity === 0

  return (
    <div className="pdp-wrap">
      {/* Breadcrumb */}
      <nav className="pdp-breadcrumb">
        <Link to="/">Home</Link> &rsaquo;
        <Link to={`/?category=${product.categoryId}`}>{product.categoryName}</Link> &rsaquo;
        <span>{product.name}</span>
      </nav>

      <div className="pdp-main">
        {/* ── LEFT: Image Gallery ──────────────────────────────── */}
        <div className="pdp-gallery">
          <div className="pdp-thumbs">
            {gallery.map((img, i) => (
              <button key={i}
                className={`pdp-thumb ${activeImg === i ? 'active' : ''}`}
                onClick={() => setActiveImg(i)}
              >
                <img src={img} alt={`view ${i+1}`} onError={imgFallback} />
              </button>
            ))}
          </div>
          <div className="pdp-main-img">
            <img
              src={gallery[activeImg] || PRODUCT_PLACEHOLDER}
              alt={product.name}
              onError={imgFallback}
            />
            {outOfStock && <div className="pdp-oos-overlay">Out of Stock</div>}
          </div>

          {/* CTA pinned below image */}
          <div className="pdp-gallery-cta">
            <div className="pdp-qty-row">
              <span className="pdp-qty-label">Qty:</span>
              <button className="qty-btn" disabled={qty<=1||outOfStock} onClick={()=>setQty(q=>q-1)}>&#8722;</button>
              <span className="qty-num">{qty}</span>
              <button className="qty-btn" disabled={qty>=product.stockQuantity||outOfStock} onClick={()=>setQty(q=>q+1)}>&#43;</button>
            </div>
            <button className="btn btn-primary btn-full pdp-cart-btn"
              disabled={outOfStock || adding}
              onClick={handleAddToCart}
            >
              {adding ? 'Adding...' : outOfStock ? 'Out of Stock' : '🛒 Add to Cart'}
            </button>
            <button className="btn btn-outline btn-full" onClick={() => navigate(-1)}>
              &larr; Back
            </button>
          </div>
        </div>

        {/* ── RIGHT: Product Info ──────────────────────────────── */}
        <div className="pdp-info">
          {/* Category + name */}
          <p className="pdp-cat">{product.categoryName}</p>
          <h1 className="pdp-name">{product.name}</h1>

          {/* Rating bar */}
          <div className="pdp-rating">
            <span className="pdp-stars">★★★★<span style={{color:'var(--border)'}}>★</span></span>
            <span className="pdp-rating-count">4.2 &nbsp;|&nbsp; 1,248 ratings</span>
          </div>

          {/* Price block */}
          <div className="pdp-price-block">
            <span className="pdp-price">{formatPrice(product.price)}</span>
            <span className="pdp-mrp">{formatPrice(mrp)}</span>
            <span className="pdp-discount">{discount}% off</span>
          </div>
          <p className="pdp-tax-note">Inclusive of all taxes. Free delivery on this order.</p>

          {/* Stock status */}
          <div className="pdp-stock">
            {outOfStock
              ? <span className="badge badge-red">Out of Stock</span>
              : stockLow
                ? <><span className="badge badge-yellow">Only {product.stockQuantity} left!</span>
                    <span className="pdp-hurry"> Hurry, order soon</span></>
                : <span className="badge badge-green">&#10003; In Stock</span>
            }
          </div>

          {/* Highlights */}
          {highlights.length > 0 && (
            <div className="pdp-section">
              <h3 className="pdp-section-title">Highlights</h3>
              <ul className="pdp-highlights">
                {highlights.map((h, i) => <li key={i}>{h}</li>)}
              </ul>
            </div>
          )}

          {/* Offers */}
          <div className="pdp-section">
            <h3 className="pdp-section-title">Available Offers</h3>
            <ul className="pdp-offers">
              <li><span className="offer-tag">Bank Offer</span> 10% off on HDFC Bank Credit Card, up to ₹1,500 on orders of ₹5,000+</li>
              <li><span className="offer-tag">Bank Offer</span> 5% Cashback on Flipkart Axis Bank Card</li>
              <li><span className="offer-tag">Special Price</span> Get extra {discount}% off (price inclusive of discount)</li>
              <li><span className="offer-tag">EMI</span> Starting from ₹{Math.round(product.price / 12).toLocaleString('en-IN')}/month on No Cost EMI</li>
            </ul>
          </div>

          {/* Delivery check */}
          <div className="pdp-section">
            <h3 className="pdp-section-title">Delivery</h3>
            <div className="pdp-pincode-row">
              <input className="form-input pdp-pincode-input"
                placeholder="Enter pincode"
                maxLength={6} value={pincode}
                onChange={e => { setPincode(e.target.value.replace(/\D/,'')); setDelivery(null) }}
              />
              <button className="btn btn-outline btn-sm" onClick={checkDelivery}>Check</button>
            </div>
            {delivery && <p className="pdp-delivery-result">&#10003; {delivery}</p>}
          </div>

          {/* Services */}
          <div className="pdp-services">
            <div className="pdp-service"><span>&#128722;</span><span>7 Day Return</span></div>
            <div className="pdp-service"><span>&#9989;</span><span>Genuine Product</span></div>
            <div className="pdp-service"><span>&#128666;</span><span>Free Delivery</span></div>
            <div className="pdp-service"><span>&#128736;</span><span>1 Year Warranty</span></div>
          </div>
        </div>
      </div>

      {/* ── TABS: Description / Specs / Reviews ─────────────────── */}
      <div className="pdp-tabs-wrap">
        <div className="pdp-tabs">
          {[['desc','Description'],['specs','Specifications'],['reviews','Reviews']].map(([k,label]) => (
            <button key={k}
              className={`pdp-tab ${activeTab===k?'active':''}`}
              onClick={() => setActiveTab(k)}
            >{label}</button>
          ))}
        </div>

        <div className="pdp-tab-content">
          {activeTab === 'desc' && (
            <div className="pdp-desc-content">
              <p>{product.description || 'No description available.'}</p>
            </div>
          )}

          {activeTab === 'specs' && (
            <table className="pdp-specs-table">
              <tbody>
                <tr><td>Product Name</td><td>{product.name}</td></tr>
                <tr><td>Category</td><td>{product.categoryName}</td></tr>
                <tr><td>Price</td><td>{formatPrice(product.price)}</td></tr>
                <tr><td>MRP</td><td>{formatPrice(mrp)}</td></tr>
                <tr><td>Discount</td><td>{discount}% off</td></tr>
                <tr><td>Availability</td><td>{outOfStock ? 'Out of Stock' : `In Stock (${product.stockQuantity} units)`}</td></tr>
                <tr><td>Sold By</td><td>ShopEase Retail Pvt. Ltd.</td></tr>
                <tr><td>Country of Origin</td><td>India</td></tr>
                <tr><td>Return Policy</td><td>7 Day Return Policy</td></tr>
                <tr><td>Warranty</td><td>1 Year Manufacturer Warranty</td></tr>
              </tbody>
            </table>
          )}

          {activeTab === 'reviews' && (
            <div className="pdp-reviews">
              <div className="pdp-rating-summary">
                <div className="pdp-big-rating">4.2 <span className="pdp-stars">★</span></div>
                <div className="pdp-rating-bars">
                  {[[5,62],[4,20],[3,8],[2,5],[1,5]].map(([star, pct]) => (
                    <div key={star} className="rating-bar-row">
                      <span>{star}★</span>
                      <div className="rating-bar"><div className="rating-fill" style={{width:`${pct}%`,background: star>=4?'var(--success)':star===3?'var(--warning)':'var(--danger)'}} /></div>
                      <span>{pct}%</span>
                    </div>
                  ))}
                </div>
              </div>
              {[
                { user:'Ravi K.', rating:5, title:'Excellent product!', body:'Exactly as described. Quality is top notch. Delivery was on time. Highly recommended.', date:'2 days ago' },
                { user:'Priya S.', rating:4, title:'Good value for money', body:'Great product overall. Packaging was secure. Minor issue with the instruction manual but product itself is perfect.', date:'1 week ago' },
                { user:'Arun M.', rating:5, title:'Worth every rupee', body:'Been using it for a month now. No complaints. Would definitely buy again and recommend to friends.', date:'2 weeks ago' },
              ].map((r, i) => (
                <div key={i} className="review-card">
                  <div className="review-header">
                    <div className="review-user-badge">{r.user[0]}</div>
                    <div>
                      <div className="review-user">{r.user}</div>
                      <div className="review-stars">{'★'.repeat(r.rating)}{'☆'.repeat(5-r.rating)}</div>
                    </div>
                    <span className="review-date">{r.date}</span>
                  </div>
                  <div className="review-title">{r.title}</div>
                  <p className="review-body">{r.body}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ── Related Products ─────────────────────────────────────── */}
      {related.length > 0 && (
        <div className="pdp-related">
          <h2 className="pdp-related-title">Similar Products</h2>
          <div className="pdp-related-grid">
            {related.map(p => (
              <Link key={p.id} to={`/products/${p.id}`} className="pdp-related-card">
                <img src={p.imageUrl || PRODUCT_PLACEHOLDER} alt={p.name} onError={imgFallback} />
                <div className="pdp-related-info">
                  <div className="pdp-related-name">{p.name}</div>
                  <div className="pdp-related-price">{formatPrice(p.price)}</div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function getDeliveryDate() {
  const d = new Date()
  d.setDate(d.getDate() + 3)
  return d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })
}
