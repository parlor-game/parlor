"""Check CI overlay routing without claiming native-device coverage."""
import asyncio
from collections import Counter
import struct
import unittest
from unittest.mock import AsyncMock, Mock, patch

from distributed_lan import (MDNS, MDNS_MAC, P2PKIT_SERVICE, address, aliases, candidates,
                             destination_mac, protocol, ready_link, rewrite)


class DistributedLanTest(unittest.TestCase):
    def setUp(self):
        self.roster = {seat: address(seat, platform).packed
                       for seat, platform in enumerate(("android", "android", "ios"), 1)}

    def ip_frame(self, source, target, protocol, payload, destination_mac=None):
        if destination_mac is None:
            destination_mac = aliases(target)
        ethernet = destination_mac + aliases(source) + b"\x08\x00"
        header = struct.pack("!BBHHHBBH4s4s", 0x45, 0, 20 + len(payload), 0, 0, 255, protocol, 0,
                             self.roster[source], MDNS if target == 0 else self.roster[target])
        return ethernet + header + payload

    def test_multicast_dns_fans_out_and_counts_native_service(self):
        dns = b"\0\0\x80\0" + b"\0" * 8 + P2PKIT_SERVICE
        udp = struct.pack("!HHHH", 5353, 5353, 8 + len(dns), 0)
        frame = self.ip_frame(1, 0, 17, udp + dns, MDNS_MAC)
        self.assertEqual([2, 3], candidates(frame, 1, self.roster))
        self.assertEqual("p2pkit_mdns_response", protocol(frame))
        self.assertEqual([], candidates(frame, 2, self.roster))

    def test_tcp_unicast_and_disallowed_udp(self):
        frame = self.ip_frame(2, 3, 6, b"\0" * 20)
        self.assertEqual([3], candidates(frame, 2, self.roster))
        self.assertTrue(destination_mac(frame, 3))
        self.assertFalse(destination_mac(frame, 1))
        udp = struct.pack("!HHHH", 2000, 8080, 8, 0)
        self.assertEqual([], candidates(self.ip_frame(2, 3, 17, udp), 2, self.roster))

    def test_shared_factory_mac_is_independent_at_each_edge(self):
        factory = bytes.fromhex("021122334455")
        arp = (factory + aliases(2) + b"\x08\x06\0\1\x08\0\x06\x04\0\2" +
               aliases(2) + self.roster[2] + factory + self.roster[1])
        outgoing = rewrite(arp, factory, aliases(1), outgoing=False)
        self.assertEqual(aliases(1), outgoing[:6])
        self.assertEqual(aliases(1), outgoing[32:38])
        self.assertEqual([1], candidates(outgoing, 2, self.roster))
        self.assertEqual(arp, rewrite(outgoing, aliases(1), factory, outgoing=False))

        local = bytes(outgoing[:6] + factory + outgoing[12:22] + factory + outgoing[28:])
        self.assertEqual(outgoing, rewrite(local, factory, aliases(2), outgoing=True))


class RelayReadinessTest(unittest.IsolatedAsyncioTestCase):
    async def test_recovers_from_one_closed_relay_during_boot(self):
        early_peer = Mock(readexactly=AsyncMock(side_effect=asyncio.IncompleteReadError(b"", 2)))
        ready_peer = Mock(readexactly=AsyncMock(return_value=b"\0\0"))
        early_writer, ready_writer = Mock(), Mock()
        open_link = AsyncMock(side_effect=[(early_peer, early_writer), (ready_peer, ready_writer)])
        counts = Counter()
        with patch("distributed_lan.asyncio.open_connection", open_link), patch(
            "distributed_lan.asyncio.sleep", AsyncMock()
        ):
            self.assertEqual((ready_peer, ready_writer), await ready_link(8788, None, counts))
        early_writer.close.assert_called_once()
        ready_writer.close.assert_not_called()
        self.assertEqual({"attach_retry:IncompleteReadError": 1}, counts)


if __name__ == "__main__":
    unittest.main()
