import { createBrowserRouter } from "react-router-dom";

import AppShell from "@/layouts/AppShell";
import AuthPage from "@/pages/Auth";
import CommercePage from "@/pages/Commerce";
import EditorPage from "@/pages/Editor";
import HomePage from "@/pages/Home";
import ProfilePage from "@/pages/Profile";
import SearchPage from "@/pages/Search";
import SettingsPage from "@/pages/Settings";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "auth", element: <AuthPage /> },
      { path: "editor", element: <EditorPage /> },
      { path: "profile", element: <ProfilePage /> },
      { path: "search", element: <SearchPage /> },
      { path: "commerce", element: <CommercePage /> },
      { path: "settings", element: <SettingsPage /> }
    ]
  }
]);

