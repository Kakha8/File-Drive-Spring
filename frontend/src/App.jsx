import {
    useCallback,
    useEffect,
    useState,
} from "react";
import {
    BrowserRouter,
    Navigate,
    Route,
    Routes,
} from "react-router-dom";

import { refresh } from "./api/auth";
import Login from "./pages/Login";
import Main from "./pages/Main";
import Trashcan from "./pages/Trashcan";
import SharedWithMe from "./pages/SharedWithMe.jsx";
import Favorites from "./pages/Favorites";
import Settings from "./pages/Settings";
import "./App.css";
import "./components/recent-activity.css";

const SIDEBAR_STORAGE_KEY = "drive-sidebar-open";

function getSavedSidebarOpen() {
    const saved = localStorage.getItem(
        SIDEBAR_STORAGE_KEY
    );

    if (saved === "true") {
        return true;
    }

    if (saved === "false") {
        return false;
    }

    return false;
}

function App() {
    const [authChecked, setAuthChecked] =
        useState(false);

    const [loggedIn, setLoggedIn] =
        useState(false);

    const [sidebarOpen, setSidebarOpen] =
        useState(getSavedSidebarOpen);

    const handleLogin = useCallback(() => {
        setLoggedIn(true);
    }, []);

    const handleLogout = useCallback(() => {
        setLoggedIn(false);
    }, []);

    const toggleSidebar = useCallback(() => {
        setSidebarOpen((current) => {
            const next = !current;

            localStorage.setItem(
                SIDEBAR_STORAGE_KEY,
                String(next)
            );

            return next;
        });
    }, []);

    useEffect(() => {
        let cancelled = false;

        async function checkAuth() {
            try {
                await refresh();

                if (!cancelled) {
                    setLoggedIn(true);
                }
            } catch {
                if (!cancelled) {
                    setLoggedIn(false);
                }
            } finally {
                if (!cancelled) {
                    setAuthChecked(true);
                }
            }
        }

        checkAuth();

        return () => {
            cancelled = true;
        };
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
                            <Navigate
                                to="/main"
                                replace
                            />
                        ) : (
                            <Login
                                onLogin={handleLogin}
                            />
                        )
                    }
                />

                <Route
                    path="/main"
                    element={
                        loggedIn ? (
                            <Main
                                onLogout={handleLogout}
                                sidebarOpen={sidebarOpen}
                                onToggleSidebar={
                                    toggleSidebar
                                }
                            />
                        ) : (
                            <Navigate
                                to="/login"
                                replace
                            />
                        )
                    }
                />

                <Route
                    path="/shared"
                    element={
                        loggedIn ? (
                            <SharedWithMe
                                onLogout={handleLogout}
                                sidebarOpen={sidebarOpen}
                                onToggleSidebar={
                                    toggleSidebar
                                }
                            />
                        ) : (
                            <Navigate
                                to="/login"
                                replace
                            />
                        )
                    }
                />

                <Route
                    path="/trashcan"
                    element={
                        loggedIn ? (
                            <Trashcan
                                onLogout={handleLogout}
                                sidebarOpen={sidebarOpen}
                                onToggleSidebar={
                                    toggleSidebar
                                }
                            />
                        ) : (
                            <Navigate
                                to="/login"
                                replace
                            />
                        )
                    }
                />

                <Route
                    path="/favorites"
                    element={
                        loggedIn ? (
                            <Favorites
                                onLogout={handleLogout}
                                sidebarOpen={sidebarOpen}
                                onToggleSidebar={
                                    toggleSidebar
                                }
                            />
                        ) : (
                            <Navigate
                                to="/login"
                                replace
                            />
                        )
                    }
                />

                <Route
                    path="/settings"
                    element={
                        loggedIn ? (
                            <Settings
                                onLogout={handleLogout}
                            />
                        ) : (
                            <Navigate
                                to="/login"
                                replace
                            />
                        )
                    }
                />

                <Route
                    path="/"
                    element={
                        <Navigate
                            to={
                                loggedIn
                                    ? "/main"
                                    : "/login"
                            }
                            replace
                        />
                    }
                />

                <Route
                    path="*"
                    element={
                        <Navigate
                            to={
                                loggedIn
                                    ? "/main"
                                    : "/login"
                            }
                            replace
                        />
                    }
                />
            </Routes>
        </BrowserRouter>
    );
}

export default App;