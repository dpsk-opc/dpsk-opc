"""Meeting Room Booking Tool for DPSK-OPC Agent.

This tool provides mock meeting room booking functionality for testing
multi-agent collaboration.
"""

from __future__ import annotations

import random
from datetime import datetime, timedelta
from typing import Any

from .base import BaseTool, ToolParameter

# Mock meeting rooms data
MOCK_MEETING_ROOMS = {
    "3楼会议室A": {"capacity": 12, "floor": 3, "equipment": ["投影仪", "白板", "电话会议"]},
    "3楼会议室B": {"capacity": 8, "floor": 3, "equipment": ["电视", "白板"]},
    "5楼会议室A": {"capacity": 20, "floor": 5, "equipment": ["投影仪", "音响", "视频会议"]},
    "5楼会议室C": {"capacity": 15, "floor": 5, "equipment": ["投影仪", "白板", "视频会议"]},
    "7楼会议室": {"capacity": 6, "floor": 7, "equipment": ["电视"]},
    "8楼大会议室": {"capacity": 50, "floor": 8, "equipment": ["投影仪", "音响", "视频会议", "直播设备"]},
}

# Mock bookings (simulated)
MOCK_BOOKINGS: list[dict[str, Any]] = []


class MeetingRoomTool(BaseTool):
    """Tool for booking meeting rooms."""

    @property
    def name(self) -> str:
        return "meeting_room"

    @property
    def description(self) -> str:
        return "会议室预定工具。用于查询可用会议室、预定会议室、取消预定等操作。"

    @property
    def skills(self) -> list[str]:
        """Skills this tool belongs to."""
        return ["office_admin"]

    @property
    def parameters(self) -> list[ToolParameter]:
        return [
            ToolParameter(
                name="action",
                param_type="string",
                description="操作类型: list（列出会议室）、available（查询可用）、book（预定）、cancel（取消）",
                required=True,
                enum=["list", "available", "book", "cancel"],
            ),
            ToolParameter(
                name="room_name",
                param_type="string",
                description="会议室名称（如 '3楼会议室A'），list/available 时可选",
                required=False,
            ),
            ToolParameter(
                name="date",
                param_type="string",
                description="日期，格式 YYYY-MM-DD，不指定则为今天",
                required=False,
            ),
            ToolParameter(
                name="start_time",
                param_type="string",
                description="开始时间，格式 HH:MM",
                required=False,
            ),
            ToolParameter(
                name="end_time",
                param_type="string",
                description="结束时间，格式 HH:MM",
                required=False,
            ),
            ToolParameter(
                name="booking_id",
                param_type="string",
                description="预定ID，用于取消预定",
                required=False,
            ),
            ToolParameter(
                name="attendees",
                param_type="integer",
                description="参会人数",
                required=False,
            ),
            ToolParameter(
                name="purpose",
                param_type="string",
                description="会议目的/标题",
                required=False,
            ),
        ]

    async def execute(
        self,
        action: str,
        room_name: str | None = None,
        date: str | None = None,
        start_time: str | None = None,
        end_time: str | None = None,
        booking_id: str | None = None,
        attendees: int | None = None,
        purpose: str | None = None,
        **kwargs: Any
    ) -> dict[str, Any]:
        """Execute meeting room booking action."""
        global MOCK_BOOKINGS

        if action == "list":
            return self._list_rooms()
        elif action == "available":
            return self._check_available(room_name, date, start_time, end_time, attendees)
        elif action == "book":
            return self._book_room(room_name, date, start_time, end_time, attendees, purpose)
        elif action == "cancel":
            return self._cancel_booking(booking_id)
        else:
            return {
                "success": False,
                "error": f"Unknown action: {action}",
            }

    def _list_rooms(self) -> dict[str, Any]:
        """List all meeting rooms."""
        rooms = []
        for name, info in MOCK_MEETING_ROOMS.items():
            rooms.append({
                "name": name,
                "capacity": info["capacity"],
                "floor": info["floor"],
                "equipment": info["equipment"],
            })

        return {
            "success": True,
            "rooms": rooms,
            "count": len(rooms),
        }

    def _check_available(
        self,
        room_name: str | None,
        date: str | None,
        start_time: str | None,
        end_time: str | None,
        attendees: int | None
    ) -> dict[str, Any]:
        """Check available meeting rooms."""
        target_date = date or datetime.now().strftime("%Y-%m-%d")

        # Filter by capacity if attendees specified
        available = []
        for name, info in MOCK_MEETING_ROOMS.items():
            if room_name and name != room_name:
                continue
            if attendees and info["capacity"] < attendees:
                continue

            # Check if room is booked for the time
            is_available = True
            if start_time and end_time:
                for booking in MOCK_BOOKINGS:
                    if booking["room_name"] == name and booking["date"] == target_date:
                        # Check time overlap
                        if self._time_overlap(start_time, end_time, booking["start_time"], booking["end_time"]):
                            is_available = False
                            break

            room_info = {
                "name": name,
                "capacity": info["capacity"],
                "floor": info["floor"],
                "equipment": info["equipment"],
                "available": is_available,
            }

            if is_available:
                available.append(room_info)

        # Sort: available first, then by capacity
        available.sort(key=lambda x: (not x["available"], -x["capacity"]))

        return {
            "success": True,
            "date": target_date,
            "time_slot": f"{start_time}-{end_time}" if start_time and end_time else "未指定",
            "requested_capacity": attendees,
            "available_rooms": available,
            "count": len(available),
        }

    def _book_room(
        self,
        room_name: str | None,
        date: str | None,
        start_time: str | None,
        end_time: str | None,
        attendees: int | None,
        purpose: str | None
    ) -> dict[str, Any]:
        """Book a meeting room."""
        global MOCK_BOOKINGS

        if not room_name:
            return {"success": False, "error": "会议室名称不能为空"}

        if room_name not in MOCK_MEETING_ROOMS:
            return {"success": False, "error": f"会议室不存在: {room_name}"}

        target_date = date or datetime.now().strftime("%Y-%m-%d")
        start = start_time or "09:00"
        end = end_time or "10:00"

        # Check if room is already booked
        for booking in MOCK_BOOKINGS:
            if (booking["room_name"] == room_name and
                booking["date"] == target_date and
                self._time_overlap(start, end, booking["start_time"], booking["end_time"])):
                return {
                    "success": False,
                    "error": f"会议室 {room_name} 在 {target_date} {start}-{end} 已被预定",
                    "booking_id": booking.get("id"),
                }

        # Create booking
        booking_id = f"BK{random.randint(1000, 9999)}"
        booking = {
            "id": booking_id,
            "room_name": room_name,
            "date": target_date,
            "start_time": start,
            "end_time": end,
            "attendees": attendees or 0,
            "purpose": purpose or "未指定",
            "booked_at": datetime.now().isoformat(),
        }

        MOCK_BOOKINGS.append(booking)

        room_info = MOCK_MEETING_ROOMS[room_name]

        return {
            "success": True,
            "message": f"会议室 {room_name} 预定成功！",
            "booking": {
                "id": booking_id,
                "room": room_name,
                "date": target_date,
                "time": f"{start} - {end}",
                "attendees": attendees,
                "purpose": purpose,
                "floor": room_info["floor"],
                "equipment": room_info["equipment"],
            },
            "tips": f"会议室位于{room_info['floor']}楼，配备: {', '.join(room_info['equipment'])}",
        }

    def _cancel_booking(self, booking_id: str | None) -> dict[str, Any]:
        """Cancel a booking."""
        global MOCK_BOOKINGS

        if not booking_id:
            return {"success": False, "error": "预定ID不能为空"}

        for i, booking in enumerate(MOCK_BOOKINGS):
            if booking["id"] == booking_id:
                MOCK_BOOKINGS.pop(i)
                return {
                    "success": True,
                    "message": f"预定 {booking_id} 已取消",
                    "room_name": booking["room_name"],
                    "date": booking["date"],
                    "time": f"{booking['start_time']} - {booking['end_time']}",
                }

        return {"success": False, "error": f"预定不存在: {booking_id}"}

    def _time_overlap(self, start1: str, end1: str, start2: str, end2: str) -> bool:
        """Check if two time ranges overlap."""
        try:
            s1 = datetime.strptime(start1, "%H:%M")
            e1 = datetime.strptime(end1, "%H:%M")
            s2 = datetime.strptime(start2, "%H:%M")
            e2 = datetime.strptime(end2, "%H:%M")
            return s1 < e2 and s2 < e1
        except ValueError:
            return False


# Tool instance
meeting_room_tool = MeetingRoomTool()
