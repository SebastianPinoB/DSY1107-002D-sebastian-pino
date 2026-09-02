import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function NavBar() {
  const { usuario, isAuthenticated, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <header className="nav">
      <div className="nav__inner">
        <NavLink to="/" className="nav__brand">
          <span className="nav__brand-mark" aria-hidden="true">
            <svg viewBox="0 0 48 48" width="28" height="28">
              <circle cx="24" cy="24" r="21" fill="none" stroke="currentColor" strokeWidth="3" />
              <path d="M14 26c2-8 6-12 10-12s8 4 10 12" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
              <circle cx="24" cy="30" r="2.5" fill="currentColor" />
            </svg>
          </span>
          Javiiland
        </NavLink>

        <nav className="nav__links">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'nav__link nav__link--active' : 'nav__link')}>
            Calendario
          </NavLink>
          {isAuthenticated && (
            <NavLink to="/reservar" className={({ isActive }) => (isActive ? 'nav__link nav__link--active' : 'nav__link')}>
              Reservar
            </NavLink>
          )}
          {isAuthenticated && (
            <NavLink to="/mis-reservas" className={({ isActive }) => (isActive ? 'nav__link nav__link--active' : 'nav__link')}>
              Mis reservas
            </NavLink>
          )}
          {isAdmin && (
            <NavLink to="/admin" className={({ isActive }) => (isActive ? 'nav__link nav__link--active' : 'nav__link')}>
              Panel admin
            </NavLink>
          )}
        </nav>

        <div className="nav__auth">
          {isAuthenticated ? (
            <>
              <span className="nav__user">
                {usuario.fullName || usuario.username}
                {isAdmin && <span className="badge badge--admin">admin</span>}
              </span>
              <button className="btn btn--ghost" onClick={handleLogout}>
                Cerrar sesión
              </button>
            </>
          ) : (
            <>
              <NavLink to="/ingresar" className="btn btn--ghost">
                Ingresar
              </NavLink>
              <NavLink to="/registro" className="btn btn--primary">
                Crear cuenta
              </NavLink>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
