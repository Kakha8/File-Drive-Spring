import { useEffect, useState } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { refresh } from "./api/auth";
import Login from "./pages/Login";
import Main from "./pages/Main";
import Trashcan from "./pages/Trashcan";
import "./App.css";

const SIDEBAR_STORAGE_KEY = "drive-sidebar-open";

function getSavedSidebarOpen() {
    const saved = localStorage.getItem(SIDEBAR_STORAGE_KEY);

    if (saved === "true") return true;
    if (saved === "false") return false;

    return false;
}

function App() {
    const [authChecked, setAuthChecked] = useState(false);
    const [loggedIn, setLoggedIn] = useState(false);
    const [sidebarOpen, setSidebarOpen] = useState(getSavedSidebarOpen);

    function toggleSidebar() {
        setSidebarOpen((current) => {
            const next = !current;
            localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
            return next;
        });
    }

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
                            <Main
                                onLogout={() => setLoggedIn(false)}
                                sidebarOpen={sidebarOpen}
                                onToggleSidebar={toggleSidebar}
                            />
                        ) : (
                            <Navigate to="/login" replace />
                        )
                    }
                />

                <Route
                    path="/trashcan"
                    element={
                        loggedIn ? (
                            <Trashcan
                                sidebarOpen={sidebarOpen}
                                onToggleSidebar={toggleSidebar}
                            />
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
