"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Activity,
  BadgeCheck,
  Building2,
  ChevronRight,
  CircleDollarSign,
  CreditCard,
  FileText,
  Folder,
  FolderKanban,
  Gauge,
  GraduationCap,
  HeartPulse,
  Kanban,
  Landmark,
  ListChecks,
  Mail,
  MessageSquare,
  ShoppingBag,
  WalletCards,
  type LucideIcon,
} from "lucide-react";

import { navigationGroups, type NavigationItem } from "@/lib/navigation";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  useSidebar,
} from "@/components/ui/sidebar";

const iconMap: Record<string, LucideIcon> = {
  activity: Activity,
  "badge-check": BadgeCheck,
  building: Building2,
  "circle-dollar-sign": CircleDollarSign,
  "credit-card": CreditCard,
  "file-text": FileText,
  folder: Folder,
  "folder-kanban": FolderKanban,
  gauge: Gauge,
  "graduation-cap": GraduationCap,
  "heart-pulse": HeartPulse,
  kanban: Kanban,
  landmark: Landmark,
  "list-checks": ListChecks,
  mail: Mail,
  "message-square": MessageSquare,
  "shopping-bag": ShoppingBag,
  wallet: WalletCards,
};

export function AppSidebar() {
  const pathname = usePathname();
  const { setOpenMobile } = useSidebar();

  return (
    <Sidebar className="border-r-0" collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              className="h-10 font-semibold text-sidebar-primary"
              tooltip="Shadcn UI Kit"
            >
              <span className="grid size-6 place-items-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
                <span className="text-xs font-black">S</span>
              </span>
              <span>Shadcn UI Kit</span>
              <ChevronRight className="ml-auto size-4 text-muted-foreground" />
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {navigationGroups.map((group) => (
          <SidebarGroup key={group.label}>
            <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map((item) => (
                  <SidebarNavigationItem
                    key={item.title}
                    item={item}
                    pathname={pathname}
                    onNavigate={() => setOpenMobile(false)}
                  />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
        <ProCard />
      </SidebarContent>
      <SidebarFooter>
        <SidebarUserMenu />
      </SidebarFooter>
    </Sidebar>
  );
}

function SidebarNavigationItem({
  item,
  pathname,
  onNavigate,
}: {
  item: NavigationItem;
  pathname: string;
  onNavigate: () => void;
}) {
  const Icon = iconMap[item.icon] ?? Gauge;
  const isActive = pathname === item.url;

  if (item.children?.length) {
    return (
      <Collapsible asChild defaultOpen={isActive} className="group/collapsible">
        <SidebarMenuItem>
          <CollapsibleTrigger asChild>
            <SidebarMenuButton tooltip={item.title}>
              <Icon />
              <span>{item.title}</span>
              <ChevronRight className="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
            </SidebarMenuButton>
          </CollapsibleTrigger>
          <CollapsibleContent>
            <SidebarMenuSub>
              {item.children.map((child) => (
                <SidebarMenuSubItem key={child.title}>
                  <SidebarMenuSubButton
                    asChild
                    isActive={pathname === child.url}
                    onClick={onNavigate}
                  >
                    <Link href={child.url}>{child.title}</Link>
                  </SidebarMenuSubButton>
                </SidebarMenuSubItem>
              ))}
            </SidebarMenuSub>
          </CollapsibleContent>
        </SidebarMenuItem>
      </Collapsible>
    );
  }

  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={isActive} tooltip={item.title}>
        <Link href={item.url} onClick={onNavigate}>
          <Icon />
          <span>{item.title}</span>
        </Link>
      </SidebarMenuButton>
      {item.badge ? <SidebarMenuBadge>{item.badge}</SidebarMenuBadge> : null}
      {item.isNew ? (
        <SidebarMenuBadge>
          <Badge className="h-5 border-emerald-300 bg-emerald-50 px-1.5 text-[10px] text-emerald-700 hover:bg-emerald-50">
            New
          </Badge>
        </SidebarMenuBadge>
      ) : null}
    </SidebarMenuItem>
  );
}

function ProCard() {
  return (
    <div className="mx-2 mb-3 mt-auto rounded-lg border bg-background p-3 shadow-sm group-data-[collapsible=icon]:hidden">
      <div className="text-sm font-semibold">Unlock Everything</div>
      <p className="mt-1 text-xs leading-5 text-muted-foreground">
        Get instant access to all premium dashboards, templates, and UI
        components. Pay once, use forever.
      </p>
      <Link
        className="mt-3 flex h-9 items-center justify-center gap-2 rounded-md bg-primary text-xs font-medium text-primary-foreground transition-colors hover:bg-primary/90"
        href="/pricing"
      >
        <span className="size-2 rounded-full bg-emerald-500" />
        Get Full Access
      </Link>
    </div>
  );
}

function SidebarUserMenu() {
  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton className="h-12">
              <Avatar className="size-8">
                <AvatarFallback className="bg-stone-900 text-xs text-white">
                  TB
                </AvatarFallback>
              </Avatar>
              <div className="min-w-0 text-left text-sm">
                <div className="truncate font-medium">Toby Belhome</div>
                <div className="truncate text-xs text-muted-foreground">
                  hello@tobybelhome.com
                </div>
              </div>
              <ChevronRight className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" side="right" className="w-56">
            <DropdownMenuLabel>Account</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Profile</DropdownMenuItem>
            <DropdownMenuItem>Billing</DropdownMenuItem>
            <DropdownMenuItem>Sign out</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
