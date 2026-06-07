import { useEffect, useState } from "react";
import { refresh } from "./api/auth";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import Main from "./pages/Main";
import Trashcan from "./pages/Trashcan";
import "./App.css";

function App() {
  const [authChecked, setAuthChecked] = useState(false);
  const [loggedIn, setLoggedIn] = useState(false);

  useEffect(() => {
    async function checkAuth() {
      try {
        await refresh();
        setLoggedIn(true);
      } catch {
        setLoggedIn(false);
      } finally {
        setAuthChecked(true);
      }
    }

    checkAuth();
  }, []);

  if (!authChecked) {
    return <div>Loading...</div>;
  }

  return (
      <BrowserRouter>
          <Routes>
              <Route
                  path="/login"
                  element={
                      loggedIn ? (
                          <Navigate to="/main" replace />
                      ) : (
                          <Login onLogin={() => setLoggedIn(true)} />
                      )
                  }
              />

              <Route
                  path="/main"
                  element={
                      loggedIn ? (
                          <Main onLogout={() => setLoggedIn(false)} />
                      ) : (
                          <Navigate to="/login" replace />
                      )
                  }
              />

              <Route
                  path="/trashcan"
                  element={
                      loggedIn ? (
                          <Trashcan />
                      ) : (
                          <Navigate to="/login" replace />
                      )
                  }
              />

              <Route
                  path="/"
                  element={<Navigate to={loggedIn ? "/main" : "/login"} replace />}
              />

              <Route
                  path="*"
                  element={<Navigate to={loggedIn ? "/main" : "/login"} replace />}
              />
          </Routes>
      </BrowserRouter>
  );
}

export default App;