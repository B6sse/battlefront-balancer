import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { MatchesPage } from './pages/MatchesPage'
import { SoundEffects } from './components/SoundEffects'
import './styles/App.scss'
import './styles/legacy.css'

function App() {
  return (
    <BrowserRouter>
      <SoundEffects />
      <Layout>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/matches" element={<MatchesPage />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  )
}

export default App
