def record_learnings():
    print("Recording learnings...")
    print("- Using CoroutineScope with SupervisorJob in DnsVpnService ensures tasks are properly cancelled and errors don't propagate to the whole scope.")
    print("- Reusing DatagramSocket in forwardDnsPacket prevents frequent socket creation/deletion and potential port exhaustion.")
    print("- Room's SearchDomains with LIKE query is more memory-efficient than loading 200k lines from assets.")
    print("- Modernizing MainActivity with ActivityResultLauncher removes deprecated onActivityResult/startActivityForResult calls.")

if __name__ == "__main__":
    record_learnings()
