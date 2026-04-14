import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { AboutPage } from './pages/AboutPage'
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
          <Route path="/about" element={<AboutPage />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  )
}

export default App
