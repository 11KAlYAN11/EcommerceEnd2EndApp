import { createContext, useContext, useState, useCallback } from 'react'
import { cartApi } from '../api/cart'
import { useAuth } from './AuthContext'

/*
 * CartContext — global cart state
 *
 * WHY global? The cart item count appears in the Navbar.
 * The Navbar doesn't know which page we're on, so it can't fetch from CartPage.
 * Instead: any page that modifies cart calls refreshCart() → count updates everywhere.
 *
 * Pattern: "lift state up" to the lowest common ancestor.
 * Navbar and CartPage are siblings under App → state lives in App-level context.
 */

const CartContext = createContext(null)

export function CartProvider({ children }) {
  const { token } = useAuth()
  const [cart, setCart]       = useState(null)   // full cart object from API
  const [loading, setLoading] = useState(false)

  const refreshCart = useCallback(async () => {
    if (!token) { setCart(null); return }
    try {
      setLoading(true)
      const res = await cartApi.getCart()
      setCart(res.data ?? res)  // interceptor returns ApiResponse; .data is the actual cart
    } catch {
      setCart(null)
    } finally {
      setLoading(false)
    }
  }, [token])

  const itemCount = cart?.items?.reduce((sum, i) => sum + i.quantity, 0) ?? 0

  return (
    <CartContext.Provider value={{ cart, setCart, itemCount, refreshCart, loading }}>
      {children}
    </CartContext.Provider>
  )
}

export function useCart() {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used inside <CartProvider>')
  return ctx
}
