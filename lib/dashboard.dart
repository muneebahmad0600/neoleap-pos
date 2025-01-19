import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:neoleap_pos/transaction_result.dart';

class Dashboard extends StatefulWidget {
  const Dashboard({super.key});

  @override
  State<Dashboard> createState() => _DashboardState();
}

class _DashboardState extends State<Dashboard> {
  late TextEditingController _amountController;
  late MethodChannel? channel;
  String ipAddress = '10.10.160.40';
  bool isConnected = false;

  @override
  void initState() {
    super.initState();
    _amountController = TextEditingController();

    channel = const MethodChannel('METHOD_CHANNEL');
    channel?.setMethodCallHandler((call) async {
      if (call.method == 'transactionResult') {
        showTransactionResult(call);
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text("Transaction Result Received")));
      }
    });
  }

  void showTransactionResult(MethodCall call) {
    try {
      final result = call.arguments as String;
      showDialog(
          context: context,
          builder: (context) {
            return AlertDialog(
              title: const Text('Transaction Result'),
              content: TransactionResult(data: json.decode(result)),
              actions: [
                TextButton(
                  onPressed: () {
                    Navigator.of(context).pop();
                  },
                  child: const Text('Close'),
                ),
              ],
            );
          });
    } catch (e) {}
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          "Sales",
          style: TextStyle(fontSize: 45, fontWeight: FontWeight.w700),
        ),
      ),
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16.0),
        child: SingleChildScrollView(
          child: Column(
            children: [
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(isConnected ? 'Connected: $ipAddress' : 'Not Connected'),
                  TextButton(
                      onPressed: () {
                        showIPAddressDialog(context);
                      },
                      child: const Text(
                        'Connect',
                        style: TextStyle(color: Colors.purple),
                      )),
                ],
              ),
              const SizedBox(height: 20),
              TextFormField(
                controller: _amountController,
                readOnly: true,
                showCursor: true,
                keyboardType: TextInputType.number,
                textInputAction: TextInputAction.done,
                decoration: const InputDecoration(
                  labelText: 'Sale Amount',
                  border: OutlineInputBorder(),
                ),
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Please enter an amount';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 40),
              CustomKeyboard(onKeyTap: onKeyTap),
              const SizedBox(height: 40),
              ElevatedButton(
                onPressed: () async {
                  if (!isConnected) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Please connect to a device'),
                      ),
                    );
                    return;
                  }
                  if (_amountController.text.isEmpty) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Please enter an amount'),
                      ),
                    );
                    return;
                  }
                  var result = await channel?.invokeMethod<bool>(
                      'startTransaction', _amountController.text);
                  if (result == true) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Waiting for transaction to complete'),
                      ),
                    );
                    _amountController.clear();
                  } else {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Transaction Failed'),
                      ),
                    );
                  }
                },
                child: const Text("Process"),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<dynamic> showIPAddressDialog(BuildContext context) {
    return showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text(
            'Connect to Device',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w700,
            ),
          ),
          content: TextFormField(
            keyboardType: const TextInputType.numberWithOptions(
              decimal: false,
              signed: false,
            ),
            initialValue: ipAddress,
            decoration: const InputDecoration(
              labelText: 'IP Address',
              border: OutlineInputBorder(),
            ),
            onChanged: (value) {
              setState(() {
                ipAddress = value;
              });
            },
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
              },
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () async {
                if (ipAddress.isEmpty) return;
                var result =
                    await channel?.invokeMethod('connectDevice', ipAddress);
                debugPrint(result.toString());
                setState(() {
                  isConnected = true;
                });
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Connected to Device'),
                  ),
                );
                Navigator.of(context).pop();
              },
              child: const Text('Connect'),
            ),
          ],
        );
      },
    );
  }

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  onKeyTap(String value) {
    if (value == 'C') {
      var text = _amountController.text;
      if (text.isEmpty) return;
      _amountController.text = text.substring(0, text.length - 1);
    } else {
      _amountController.text = _amountController.text + value;
    }
  }
}

class CustomKeyboard extends StatelessWidget {
  final Function(String) onKeyTap;

  const CustomKeyboard({super.key, required this.onKeyTap});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            CustomKey(keyLabel: '1', onKeyTap: onKeyTap),
            CustomKey(keyLabel: '2', onKeyTap: onKeyTap),
            CustomKey(keyLabel: '3', onKeyTap: onKeyTap),
          ],
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            CustomKey(keyLabel: '4', onKeyTap: onKeyTap),
            CustomKey(keyLabel: '5', onKeyTap: onKeyTap),
            CustomKey(keyLabel: '6', onKeyTap: onKeyTap),
          ],
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            CustomKey(keyLabel: '7', onKeyTap: onKeyTap),
            CustomKey(keyLabel: '8', onKeyTap: onKeyTap),
            CustomKey(keyLabel: '9', onKeyTap: onKeyTap),
          ],
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            CustomKey(keyLabel: '', onKeyTap: onKeyTap),
            CustomKey(keyLabel: '0', onKeyTap: onKeyTap),
            CustomKey(keyLabel: 'C', onKeyTap: onKeyTap),
          ],
        ),
      ],
    );
  }
}

class CustomKey extends StatelessWidget {
  final String keyLabel;
  final Function(String) onKeyTap;

  const CustomKey({super.key, required this.keyLabel, required this.onKeyTap});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: () => onKeyTap(keyLabel),
        onLongPress: () => _onLongPress(context),
        splashColor: Colors.white38,
        child: Container(
          padding: const EdgeInsets.all(16),
          child: Center(
            child: Text(
              keyLabel,
              style: const TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ),
      ),
    );
  }

  // Callback for long press
  void _onLongPress(BuildContext context) {
    // You can call onKeyTap repeatedly or perform any other action
    onKeyTap(keyLabel);
  }
}
