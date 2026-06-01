"use client";

import * as React from "react";

import { AppSidebar } from "@/components/app-shell/app-sidebar";
import { TopBar } from "@/components/app-shell/top-bar";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-w-0 overflow-hidden bg-background md:m-2 md:rounded-xl md:border md:shadow-sm">
        <TopBar />
        <main className="min-w-0 px-4 pb-4 pt-5 sm:px-6">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
