import * as React from 'react'
import { ChevronsUpDown, BarChart3, Zap, GitBranch, Database } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'

type TeamSwitcherProps = {
  teams: {
    name: string
    logo: React.ElementType
    plan: string
  }[]
}

export function TeamSwitcher({ teams }: TeamSwitcherProps) {
  const { isMobile } = useSidebar()
  const [activeTeam, setActiveTeam] = React.useState(teams[0])

  const handleExternalLink = (url: string) => {
    window.open(url, '_blank', 'noopener,noreferrer')
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size='lg'
              className='data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground'
            >
              <div className='bg-sidebar-primary text-sidebar-primary-foreground flex aspect-square size-8 items-center justify-center rounded-lg'>
                <activeTeam.logo className='size-4' />
              </div>
              <div className='grid flex-1 text-start text-sm leading-tight'>
                <span className='truncate font-semibold'>
                  {activeTeam.name}
                </span>
                <span className='truncate text-xs'>{activeTeam.plan}</span>
              </div>
              <ChevronsUpDown className='ms-auto' />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className='w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg'
            align='start'
            side={isMobile ? 'bottom' : 'right'}
            sideOffset={4}
          >
            <DropdownMenuLabel className='text-muted-foreground text-xs'>
              Systems
            </DropdownMenuLabel>
            {teams.map((team, index) => (
              <DropdownMenuItem
                key={team.name}
                onClick={() => setActiveTeam(team)}
                className='gap-2 p-2'
              >
                <div className='flex size-6 items-center justify-center rounded-sm border'>
                  <team.logo className='size-4 shrink-0' />
                </div>
                {team.name}
                <DropdownMenuShortcut>⌘{index + 1}</DropdownMenuShortcut>
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
            <DropdownMenuLabel className='text-muted-foreground text-xs'>
              Analytics
            </DropdownMenuLabel>
            <DropdownMenuItem
              onClick={() => handleExternalLink('https://www.google.com')}
              className='gap-2 p-2'
            >
              <div className='flex size-6 items-center justify-center rounded-sm border'>
                <BarChart3 className='size-4 shrink-0' />
              </div>
              Redash
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => handleExternalLink('https://www.google.com')}
              className='gap-2 p-2'
            >
              <div className='flex size-6 items-center justify-center rounded-sm border'>
                <BarChart3 className='size-4 shrink-0' />
              </div>
              Superset
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => handleExternalLink('https://www.google.com')}
              className='gap-2 p-2'
            >
              <div className='flex size-6 items-center justify-center rounded-sm border'>
                <BarChart3 className='size-4 shrink-0' />
              </div>
              Jupyter
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuLabel className='text-muted-foreground text-xs'>
              Realtime
            </DropdownMenuLabel>
            <DropdownMenuItem
              onClick={() => handleExternalLink('https://www.google.com')}
              className='gap-2 p-2'
            >
              <div className='flex size-6 items-center justify-center rounded-sm border'>
                <Zap className='size-4 shrink-0' />
              </div>
              Kafka UI
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuLabel className='text-muted-foreground text-xs'>
              Warehouse
            </DropdownMenuLabel>
            <DropdownMenuItem
              onClick={() => handleExternalLink('https://www.google.com')}
              className='gap-2 p-2'
            >
              <div className='flex size-6 items-center justify-center rounded-sm border'>
                <GitBranch className='size-4 shrink-0' />
              </div>
              Workflow
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => handleExternalLink('https://www.google.com')}
              className='gap-2 p-2'
            >
              <div className='flex size-6 items-center justify-center rounded-sm border'>
                <Database className='size-4 shrink-0' />
              </div>
              Warehouse
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
