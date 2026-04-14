import { useState, useCallback } from 'react'
import { NavLink } from 'react-router-dom'
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

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `link nav__element--link sound__hover sound__click${isActive ? ' nav__element--active' : ''}`

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
            <NavLink className="link btn btn--login icon--login sound__hover sound__click" to="/login" aria-label="Log in" />
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
              <NavLink end className={navClass} to="/" onClick={closeMenu}>
                Play
              </NavLink>
            </li>
            <li className="nav__element">
              <NavLink className={navClass} to="/matches" onClick={closeMenu}>
                Matches
              </NavLink>
            </li>
            <li className="nav__element">
              <NavLink className={navClass} to="/stats" onClick={closeMenu}>
                Stats
              </NavLink>
            </li>
            <li className="nav__element">
              <NavLink className={navClass} to="/about" onClick={closeMenu}>
                About
              </NavLink>
            </li>
          </ul>
        </nav>
      </div>
    </header>
  )
}
