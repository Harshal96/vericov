"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Activity,
  AudioLines,
  BadgeCheck,
  BadgeDollarSign,
  Bell,
  Blocks,
  Building2,
  Calendar,
  ChevronRight,
  CircleOff,
  CircleDollarSign,
  CreditCard,
  Download,
  FileText,
  Folder,
  FolderKanban,
  Gauge,
  Github,
  Globe,
  GraduationCap,
  HeartPulse,
  Image,
  Kanban,
  Key,
  Landmark,
  LayoutDashboard,
  ListChecks,
  Mail,
  MessageSquare,
  MoreHorizontal,
  PanelTop,
  Plus,
  Route,
  ScanEye,
  Settings,
  ShoppingBag,
  ShieldCheck,
  TriangleAlert,
  User,
  UserRound,
  Users,
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
  "audio-lines": AudioLines,
  "badge-check": BadgeCheck,
  "badge-dollar-sign": BadgeDollarSign,
  bell: Bell,
  blocks: Blocks,
  building: Building2,
  calendar: Calendar,
  "circle-dollar-sign": CircleDollarSign,
  "circle-off": CircleOff,
  "credit-card": CreditCard,
  download: Download,
  "file-text": FileText,
  folder: Folder,
  "folder-kanban": FolderKanban,
  gauge: Gauge,
  github: Github,
  globe: Globe,
  "graduation-cap": GraduationCap,
  "heart-pulse": HeartPulse,
  image: Image,
  kanban: Kanban,
  key: Key,
  landmark: Landmark,
  "layout-dashboard": LayoutDashboard,
  "list-checks": ListChecks,
  mail: Mail,
  "message-square": MessageSquare,
  "panel-top": PanelTop,
  route: Route,
  "scan-eye": ScanEye,
  settings: Settings,
  "shopping-bag": ShoppingBag,
  "shield-check": ShieldCheck,
  "triangle-alert": TriangleAlert,
  user: User,
  "user-round": UserRound,
  users: Users,
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
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <SidebarMenuButton
                  className="h-10 font-semibold text-sidebar-primary"
                  tooltip="Shadcn UI Kit"
                >
                  <ShadcnMark />
                  <span>Shadcn UI Kit</span>
                  <ChevronRight className="ml-auto size-4 text-muted-foreground" />
                </SidebarMenuButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent
                align="end"
                className="mt-4 w-[var(--radix-dropdown-menu-trigger-width)] min-w-56 rounded-lg"
                side="right"
              >
                <DropdownMenuLabel className="text-xs text-muted-foreground">
                  Projects
                </DropdownMenuLabel>
                <DropdownMenuItem className="gap-3 p-2">
                  <span className="grid size-8 place-items-center rounded-md border bg-background">
                    <ShoppingBag className="size-4" />
                  </span>
                  <span className="grid flex-1 text-sm leading-tight">
                    <span className="font-medium">E-commerce</span>
                    <span className="text-xs text-muted-foreground">
                      Active
                    </span>
                  </span>
                </DropdownMenuItem>
                <DropdownMenuItem className="gap-3 p-2">
                  <span className="grid size-8 place-items-center rounded-md border bg-background">
                    <PanelTop className="size-4" />
                  </span>
                  <span className="grid flex-1 text-sm leading-tight">
                    <span className="font-medium">Blog Platform</span>
                    <span className="text-xs text-muted-foreground">
                      Inactive
                    </span>
                  </span>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem className="gap-2">
                  <Plus className="size-4" />
                  New Project
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
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
      </SidebarContent>
      <SidebarFooter>
        <ProCard />
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
    <div className="flex min-h-[210px] flex-col rounded-lg border bg-background p-3 shadow-sm group-data-[collapsible=icon]:hidden">
      <div className="text-sm font-semibold">Unlock Everything</div>
      <p className="mt-1 text-xs leading-5 text-muted-foreground">
        Get instant access to all premium dashboards, templates, and UI
        components. Pay once, use forever in unlimited projects.
      </p>
      <Link
        className="mt-auto flex h-9 items-center justify-center gap-2 rounded-md bg-primary text-xs font-medium text-primary-foreground transition-colors hover:bg-primary/90"
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
              <MoreHorizontal className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" side="right" className="w-60">
            <DropdownMenuLabel>
              <div className="grid text-sm leading-tight">
                <span>Toby Belhome</span>
                <span className="text-xs font-normal text-muted-foreground">
                  hello@tobybelhome.com
                </span>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Upgrade to Pro</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Account</DropdownMenuItem>
            <DropdownMenuItem>Billing</DropdownMenuItem>
            <DropdownMenuItem>Notifications</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Log out</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}

function ShadcnMark() {
  return (
    <span className="relative grid size-6 place-items-center overflow-hidden rounded-md bg-sidebar-primary">
      <span className="absolute h-1 w-4 rotate-45 rounded-full bg-sidebar-primary-foreground" />
      <span className="absolute h-1 w-4 rotate-45 rounded-full bg-sidebar-primary-foreground [transform:translateY(-5px)_rotate(45deg)]" />
      <span className="absolute h-1 w-4 rotate-45 rounded-full bg-sidebar-primary-foreground [transform:translateY(5px)_rotate(45deg)]" />
    </span>
  );
}
