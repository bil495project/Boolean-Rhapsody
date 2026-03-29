import { useEffect, useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { CssVarsProvider } from '@mui/joy/styles';
import CssBaseline from '@mui/joy/CssBaseline';
import { Box, CircularProgress } from '@mui/joy';
import { GoogleReCaptchaProvider } from 'react-google-recaptcha-v3';
import { Provider } from 'react-redux';
import { store } from './store';
import theme from './theme/theme';
import { RECAPTCHA_SITE_KEY } from './utils/recaptcha';
import { useAppDispatch, useAppSelector } from './store/hooks';
import { restoreSession } from './store/authSlice';
import { fetchChats } from './store/chatSlice';
import { fetchSavedFromBackend } from './store/savedSlice';

// Pages
import LandingPage from './pages/LandingPage';
import AuthPage from './pages/AuthPage';
import TravelPersonaPage from './pages/TravelPersonaPage';
import ProfilePage from './pages/ProfilePage';
import SettingsPage from './pages/AccountSettingsPage';
import ChatPage from './pages/ChatPage';
import ExplorePage from './pages/ExplorePage';
import SavedPage from './pages/SavedPage';
import SavedRoutesPage from './pages/SavedRoutesPage';
import NavigationPage from './pages/NavigationPage';
import RoutePage from './pages/RoutePage';

// Redirect component for /chat - goes to existing chat or new chat view
const ChatRedirect = () => {
  return <Navigate to="/chat/new" replace />;
};

// Inner app component (inside Provider) to use hooks
function AppInner() {
  const dispatch = useAppDispatch();
  const { isAuthenticated } = useAppSelector((state) => state.auth);
  const [isRestoring, setIsRestoring] = useState(true);

  useEffect(() => {
    dispatch(restoreSession()).finally(() => {
      setIsRestoring(false);
    });
  }, [dispatch]);

  // Fetch data when authenticated (after login or session restore)
  useEffect(() => {
    if (isAuthenticated) {
      dispatch(fetchChats());
      dispatch(fetchSavedFromBackend());
    }
  }, [isAuthenticated, dispatch]);

  if (isRestoring) {
    return (
      <CssVarsProvider theme={theme} defaultMode="system">
        <CssBaseline />
        <Box sx={{ display: 'flex', height: '100vh', width: '100vw', justifyContent: 'center', alignItems: 'center' }}>
          <CircularProgress />
        </Box>
      </CssVarsProvider>
    );
  }

  return (
    <CssVarsProvider theme={theme} defaultMode="system">
      <CssBaseline />
      <Router>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/auth" element={<AuthPage />} />
          <Route path="/login" element={<Navigate to="/auth" replace />} />
          <Route path="/signup" element={<Navigate to="/auth" replace />} />
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/saved" element={<SavedPage />} />
          <Route path="/saved-routes" element={<SavedRoutesPage />} />
          <Route path="/navigation" element={<NavigationPage />} />
          <Route path="/route" element={<RoutePage />} />
          <Route path="/route/saved/:savedRouteId" element={<RoutePage />} />
          <Route path="/onboarding" element={<TravelPersonaPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/chat" element={<ChatRedirect />} />
          <Route path="/chat/new" element={<ChatPage />} />
          <Route path="/chat/:chatId" element={<ChatPage />} />
        </Routes>
      </Router>
    </CssVarsProvider>
  );
}

function App() {
  return (
    <Provider store={store}>
      <GoogleReCaptchaProvider reCaptchaKey={RECAPTCHA_SITE_KEY}>
        <AppInner />
      </GoogleReCaptchaProvider>
    </Provider>
  );
}

export default App;
