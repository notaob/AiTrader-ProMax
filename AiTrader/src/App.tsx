import { BrowserRouter as Router, Routes, Route,  } from 'react-router-dom'
import { Home } from './pages/Home'
import { Deals } from './pages/Deals'
import { Moments } from './pages/Moments'
import { Me } from './pages/Me'
import { BottomNav } from './components/BottomNav'
import './assets/styles/App.css'
import { ForgotPassword } from './pages/ForgotPassword'
import { Register } from './pages/Register'
import { Login } from './pages/Login'
import { About } from './pages/About'
import { Kyc } from './pages/Kyc'
import { Modal } from './components/Modal'
import { AuthProvider, useAuth } from './context/AuthContext'
import { NewMoment } from './pages/NewMoment'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Security } from './pages/Security'
import { StrategyReport } from './pages/StrategyReport'

// 创建一个内部组件来使用路由 hook
const AppContent = () => {
  const { authModalView, closeAuthModal } = useAuth();

  return (
    <div className="app-container">
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/deals" element={<Deals />} />
        <Route path="/moments" element={<Moments />} />
        <Route path="/me" element={<Me />} />
        <Route path="/about" element={<About />} />
        <Route path="/report" element={<StrategyReport />} />
        <Route path="/kyc" element={
          <ProtectedRoute>
            <Kyc />
          </ProtectedRoute>
        } />
        <Route path="/security" element={
          <ProtectedRoute>
            <Security />
          </ProtectedRoute>
        } />
        <Route path="/moments/new" element={
          <ProtectedRoute>
            <NewMoment />
          </ProtectedRoute>
        } />
      </Routes>
      
      <BottomNav />

      <Modal isOpen={authModalView !== null} onClose={closeAuthModal}>
        {authModalView === 'login' && <Login />}
        {authModalView === 'register' && <Register />}
        {authModalView === 'forgot-password' && <ForgotPassword />}
      </Modal>
    </div>
  )
}

function App() {
  return (
    <AuthProvider>
      <Router>
        <AppContent />
      </Router>
    </AuthProvider>
  )
}

export default App
