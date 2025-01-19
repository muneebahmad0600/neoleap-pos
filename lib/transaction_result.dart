import 'package:flutter/material.dart';

class TransactionResult extends StatelessWidget {
  final dynamic data;
  const TransactionResult({super.key, this.data});

  @override
  Widget build(BuildContext context) {
    final transactionDateTime = data['PerformanceStartDateTime'];
    final rrn = data['RRN'];
    final typeEng = data['TransactionTypeEnglish'] ?? data['TRANSACTION_TYPE'];
    final amount = data['Amount'];
    final appr = data['ApprovalCode'];
    final statusCode = data['TerminalStatusCode'];
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(statusCode == "00" || statusCode == "0000"
            ? "APPROVED"
            : "DECLINED"),
        const SizedBox(height: 20),
        Text("Type  $typeEng"),
        Text("Amount  $amount"),
        Text("Date  $transactionDateTime"),
        Text("Appr  $appr"),
        Text("RRN  $rrn"),
        // const SizedBox(height: 20),
        // Text(data['TERMINAL_STATUS']),
      ],
    );
  }
}
