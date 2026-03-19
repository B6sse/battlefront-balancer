import { useState, useCallback } from 'react'
import burgerUrl from '../assets/images/SVG/burger.svg'
import crossUrl from '../assets/images/SVG/cross.svg'
import adminIcon from '../assets/images/dune_sea_exchange.jpg'

export function Header() {
  const [menuOpen, setMenuOpen] = useState(false)

  const toggleMenu = useCallback(() => {
    setMenuOpen((prev) => {
      const next = !prev
      if (typeof document !== 'undefined') {
        if (next) {
          document.body.setAttribute('data-menu', 'true')
        } else {
          document.body.removeAttribute('data-menu')
        }
      }
      return next
    })
  }, [])

  const closeMenu = useCallback(() => {
    setMenuOpen(false)
    document.body.removeAttribute('data-menu')
  }, [])

  return (
    <header className="header">
      <div className="container">
        <div className="admin">
          <div className="admin__settings">
            <div className="admin__info">
              <div className="admin__session">
                <img
                  className="admin__picture"
                  src={adminIcon}
                  alt="Profile"
                />
                <div className="admin__status admin__status--disconnected" />
              </div>
            </div>
            <a className="link btn btn--login icon--login" href="/login" aria-label="Log in" />
          </div>
          <button
            type="button"
            className="btn btn--menu"
            onClick={toggleMenu}
            aria-label="Menu"
            aria-expanded={menuOpen}
          >
            <img
              className="burger-menu"
              src={menuOpen ? crossUrl : burgerUrl}
              alt=""
            />
          </button>
        </div>
        <nav className="nav">
          <ul className="list nav__list">
            <li className="nav__element">
              <a className="link nav__element--link nav__element--active" href="/" onClick={closeMenu}>
                Play
              </a>
            </li>
            <li className="nav__element">
              <a className="link nav__element--link" href="/matches" onClick={closeMenu}>
                Matches
              </a>
            </li>
            <li className="nav__element">
              <a className="link nav__element--link" href="/stats" onClick={closeMenu}>
                Stats
              </a>
            </li>
            <li className="nav__element">
              <a className="link nav__element--link" href="/about" onClick={closeMenu}>
                About
              </a>
            </li>
          </ul>
        </nav>
      </div>
    </header>
  )
}
