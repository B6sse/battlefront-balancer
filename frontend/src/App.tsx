import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { MatchesPage } from './pages/MatchesPage'
import { StatsPage } from './pages/StatsPage'
import { InternPage } from './pages/InternPage'
import { RankedPage } from './pages/RankedPage'
import { LoginPage } from './pages/LoginPage'
import { AdminPage } from './pages/AdminPage'
import { SoundEffects } from './components/SoundEffects'
import { AuthProvider } from './context/AuthContext'
import { RequireRole } from './components/RequireRole'
import './styles/App.scss'
import './styles/legacy.css'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <SoundEffects />
        <Routes>
          <Route path="/intern" element={<InternPage />} />
          <Route path="/ranked" element={<RankedPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="*"
            element={
              <Layout>
                <Routes>
                  <Route path="/" element={<HomePage />} />
                  <Route path="/matches" element={<MatchesPage />} />
                  <Route path="/stats" element={<StatsPage />} />
                  <Route
                    path="/admin"
                    element={
                      <RequireRole roles={['admin', 'editor']}>
                        <AdminPage />
                      </RequireRole>
                    }
                  />
                </Routes>
              </Layout>
            }
          />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
